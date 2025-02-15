/*
 * Copyright 2023 Conduktor, Inc
 *
 * Licensed under the Conduktor Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * https://www.conduktor.io/conduktor-community-license-agreement-v1.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.conduktor.gateway.service;

import io.conduktor.gateway.config.InterceptorConfigEntry;
import io.conduktor.gateway.config.InterceptorPluginConfig;
import io.conduktor.gateway.config.GatewayConfiguration;
import io.conduktor.gateway.interceptor.InterceptorValue;
import io.conduktor.gateway.interceptor.Plugin;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.requests.AbstractRequest;
import org.apache.kafka.common.requests.AbstractRequestResponse;
import org.apache.kafka.common.requests.AbstractResponse;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static io.conduktor.gateway.config.support.Messages.BAD_INTERCEPTOR_CONFIG;

@SuppressWarnings("rawtypes")
@Slf4j
public class InterceptorPoolService {

    private final GatewayConfiguration gatewayConfiguration;

    private final Map<Class<?>, List<InterceptorValue>> interceptors = new HashMap<>();

    @Inject
    public InterceptorPoolService(GatewayConfiguration gatewayConfiguration) {
        this.gatewayConfiguration = gatewayConfiguration;
        try {
            loadInterceptors();
        } catch (Exception e) {
            log.error("Failed to load interceptors", e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadInterceptors() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        validateInterceptorPluginConfigs(gatewayConfiguration.getInterceptors());
        for (InterceptorPluginConfig config : gatewayConfiguration.getInterceptors()) {
            Class classToInstantiate = Class.forName(config.getPluginClass());
            // features must have a blank constructor
            var plugin = (Plugin) classToInstantiate.getDeclaredConstructor().newInstance();
            var pluginConfigs = config.getConfig().stream()
                    .peek(configValue -> {
                        if (Objects.isNull(configValue.getKey())) {
                            throw new IllegalArgumentException("key for config " + config.getPluginClass() + " can not be null");
                        }
                        if (Objects.isNull(configValue.getValue())) {
                            throw new IllegalArgumentException("value for config " +  config.getPluginClass() + " of key " + configValue.getKey() +  " can not be null");
                        }
                    })
                    .collect(Collectors.toMap(InterceptorConfigEntry::getKey, InterceptorConfigEntry::getValue));
            plugin.configure(pluginConfigs);
            plugin.getTypedInterceptors().forEach((type, interceptorsForType) -> {
                if (!interceptors.containsKey(type)) {
                    interceptors.put(type, new ArrayList<>());
                }
                interceptors.get(type).addAll(interceptorsForType.stream()
                        .map(interceptor -> new InterceptorValue(interceptor, config.getPriority()))
                        .toList());
            });
        }
    }

    public List<InterceptorValue> getAllInterceptors(Class<?> inputType) {
        var filteredInterceptors = new ArrayList<InterceptorValue>();
        // add specific ones
        if (!List.of(AbstractRequestResponse.class,
                AbstractRequest.class,
                AbstractResponse.class).contains(inputType)
                && interceptors.containsKey(inputType)) {
            filteredInterceptors.addAll(interceptors.get(inputType));
        }
        // add directional ones
        if (interceptors.containsKey(AbstractRequest.class) && AbstractRequest.class.isAssignableFrom(inputType)) {
            filteredInterceptors.addAll(interceptors.get(AbstractRequest.class));
        }
        if (interceptors.containsKey(AbstractResponse.class) && AbstractResponse.class.isAssignableFrom(inputType)) {
            filteredInterceptors.addAll(interceptors.get(AbstractResponse.class));
        }

        // add everything ones
        if (interceptors.containsKey(AbstractRequestResponse.class)) {
            filteredInterceptors.addAll(interceptors.get(AbstractRequestResponse.class));
        }
        return filteredInterceptors;
    }

    private void validateInterceptorPluginConfigs(List<InterceptorPluginConfig> interceptors) {
        var isDuplicated = interceptors.stream()
                .collect(Collectors.groupingBy(InterceptorPluginConfig::getName))
                .entrySet()
                .stream()
                .anyMatch(e -> e.getValue().size() > 1);
        if (isDuplicated) {
            throw new IllegalArgumentException("Interceptor plugin config already exists");
        }
    }

}
