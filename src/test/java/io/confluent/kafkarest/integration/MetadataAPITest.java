/**
 * Copyright 2015 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.confluent.kafkarest.integration;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import io.confluent.kafkarest.Errors;
import io.confluent.kafkarest.Versions;
import io.confluent.kafkarest.entities.BrokerList;
import io.confluent.kafkarest.entities.Partition;
import io.confluent.kafkarest.entities.Topic;
import kafka.utils.TestUtils;
import scala.collection.JavaConversions;

import static io.confluent.kafkarest.TestUtils.assertErrorResponse;
import static io.confluent.kafkarest.TestUtils.assertOKResponse;
import static org.junit.Assert.assertEquals;

/**
 * Tests metadata access against a real cluster. This isn't exhaustive since the unit tests cover
 * corner cases; rather it verifies the basic functionality works against a real cluster.
 */
public class MetadataAPITest extends ClusterTestHarness {

  private static final String topic1Name = "topic1";
  private static final int topic1Partitions = 1;
  private static final Topic topic1 = new Topic(topic1Name, topic1Partitions, new Properties());
  private static final String topic2Name = "topic2";
  private static final int topic2Partitions = 2;
  private static final Properties topic2Configs;
  private static final Topic topic2;
  static {
    topic2Configs = new Properties();
    topic2Configs.setProperty("cleanup.policy", "delete");
    topic2 = new Topic(topic2Name, topic2Partitions, topic2Configs);
  }

  private static final int numReplicas = 2;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestUtils.createTopic(zkClient, topic1Name, topic1Partitions, numReplicas,
                          JavaConversions.asScalaIterable(this.servers).toSeq(), new Properties());
    TestUtils.createTopic(zkClient, topic2Name, topic2Partitions, numReplicas,
                          JavaConversions.asScalaIterable(this.servers).toSeq(), topic2Configs);
  }

  @Test
  public void testBrokers() throws InterruptedException {
    // Listing
    Response response = request("/brokers").get();
    assertOKResponse(response, Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
    final BrokerList brokers = response.readEntity(BrokerList.class);
    assertEquals(new BrokerList(Arrays.asList(0, 1, 2)), brokers);
  }

  @Test
  public void testTopicsList() throws InterruptedException {
    // Listing
    Response response = request("/topics").get();
    assertOKResponse(response, Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
    final List<Topic> topicsResponse = response.readEntity(new GenericType<List<Topic>>() {
    });
    assertEquals(Arrays.asList(topic1, topic2), topicsResponse);

    // Get topic
    Response response1 = request("/topics/{topic}", "topic", topic1Name).get();
    assertOKResponse(response, Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
    final Topic topic1Response = response1.readEntity(Topic.class);
    assertEquals(topic1, topic1Response);

    // Get invalid topic
    final Response invalidResponse = request("/topics/{topic}", "topic", "topicdoesntexist").get();
    assertErrorResponse(Response.Status.NOT_FOUND, invalidResponse,
                        Errors.TOPIC_NOT_FOUND_ERROR_CODE,
                        Errors.TOPIC_NOT_FOUND_MESSAGE,
                        Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
  }

  @Test
  public void testPartitionsList() throws InterruptedException {
    // Listing
    Response response = request("/topics/" + topic1Name + "/partitions").get();
    assertOKResponse(response, Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
    final List<Partition>
        partitions1Response =
        response.readEntity(new GenericType<List<Partition>>() {
        });
    // Just verify some basic properties because the exact values can vary based on replica
    // assignment, leader election
    assertEquals(topic1Partitions, partitions1Response.size());
    assertEquals(numReplicas, partitions1Response.get(0).getReplicas().size());

    response = request("/topics/" + topic2Name + "/partitions").get();
    assertOKResponse(response, Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
    final List<Partition>
        partitions2Response =
        response.readEntity(new GenericType<List<Partition>>() {
        });
    assertEquals(topic2Partitions, partitions2Response.size());
    assertEquals(numReplicas, partitions2Response.get(0).getReplicas().size());
    assertEquals(numReplicas, partitions2Response.get(1).getReplicas().size());

    // Get single partition
    response = request("/topics/" + topic1Name + "/partitions/0").get();
    assertOKResponse(response, Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
    final Partition getPartitionResponse = response.readEntity(Partition.class);
    assertEquals(0, getPartitionResponse.getPartition());
    assertEquals(numReplicas, getPartitionResponse.getReplicas().size());

    // Get invalid partition
    final Response invalidResponse = request("/topics/topic1/partitions/1000").get();
    assertErrorResponse(Response.Status.NOT_FOUND, invalidResponse,
                        Errors.PARTITION_NOT_FOUND_ERROR_CODE,
                        Errors.PARTITION_NOT_FOUND_MESSAGE,
                        Versions.KAFKA_MOST_SPECIFIC_DEFAULT);
  }
}
