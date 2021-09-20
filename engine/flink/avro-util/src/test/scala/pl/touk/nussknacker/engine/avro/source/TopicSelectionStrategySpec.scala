package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.{AllTopicsSelectionStrategy, TopicPatternSelectionStrategy, TopicSelectionStrategy}
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

import java.util.regex.Pattern

class TopicSelectionStrategySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private lazy val confluentClient = schemaRegistryProvider.schemaRegistryClientFactory.create(kafkaConfig)

  test("all topic strategy test") {
    val strategy = new AllTopicsSelectionStrategy()
    strategy.getTopics(confluentClient).toList.map(_.toSet) shouldBe List(Set(RecordTopic, RecordTopicWithKey, IntTopicNoKey, IntTopicWithKey, InvalidDefaultsTopic, PaymentDateTopic, GeneratedWithLogicalTypesTopic))
  }

  test("topic filtering strategy test") {
    val strategy = new TopicPatternSelectionStrategy(Pattern.compile(".*Record.*"))
    strategy.getTopics(confluentClient).toList shouldBe List(List(RecordTopic, RecordTopicWithKey))
  }

  test("show how to override topic selection strategy") {
    new KafkaAvroSourceFactory(schemaRegistryProvider, testProcessObjectDependencies, None) {
      override def topicSelectionStrategy = new TopicPatternSelectionStrategy(Pattern.compile("test-.*"))
    }
  }

  class OnlyExistingTopicSelectionStrategy(kafkaConfig: KafkaConfig) extends TopicSelectionStrategy {
    override def getTopics(schemaRegistryClient: SchemaRegistryClient): Validated[SchemaRegistryError, List[String]] = {
      val existingTopics = KafkaUtils.getTopics(kafkaConfig) // or, for consistency, use topics cached within CachedTopicsExistenceValidator (maybe cache outside the validator?)
      schemaRegistryClient.getAllTopics.map(all => all.intersect(existingTopics))
    }
  }

  test("show how to use only existing topics strategy") {
    new KafkaAvroSourceFactory(schemaRegistryProvider, testProcessObjectDependencies, None) {
      override def topicSelectionStrategy = new OnlyExistingTopicSelectionStrategy(this.kafkaConfig)
    }
  }

}
