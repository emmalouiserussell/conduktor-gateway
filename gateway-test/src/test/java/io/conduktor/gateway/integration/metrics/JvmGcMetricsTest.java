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

package io.conduktor.gateway.integration.metrics;

import io.conduktor.gateway.integration.BaseGatewayIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JvmGcMetricsTest extends BaseGatewayIntegrationTest {
    @Test
    void gcMetricsAvailableAfterGc() {
        var registry = getMetricsRegistryProvider().registry();
        System.gc();
        await().timeout(5000, TimeUnit.MILLISECONDS).alias("NotificationListener takes time after GC")
                .untilAsserted(() -> assertThat(registry.find("jvm.gc.live.data.size").gauge().value()).isPositive());
        assertThat(registry.find("jvm.gc.max.data.size").gauge().value()).isPositive();

        // cannot guarantee an object was promoted, so cannot check for positive count
        assertThat(registry.find("jvm.gc.memory.promoted").counter()).isNotNull();
    }
}
