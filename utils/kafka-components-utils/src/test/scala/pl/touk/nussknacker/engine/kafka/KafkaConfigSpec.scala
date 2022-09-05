package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class KafkaConfigSpec extends AnyFunSuite with Matchers {

  test("parse config") {
    val typesafeConfig = ConfigFactory.parseString(
      """kafka {
        |  lowLevelComponentsEnabled: false
        |  kafkaProperties {
        |    "bootstrap.servers": "localhost:9092"
        |    "auto.offset.reset": latest
        |  }
        |}""".stripMargin)
    val expectedConfig = KafkaConfig(Map("bootstrap.servers" -> "localhost:9092", "auto.offset.reset" -> "latest"), None, None)
    KafkaConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

  test("parse config with topicExistenceValidation") {
    val typesafeConfig = ConfigFactory.parseString(
      """kafka {
        |  kafkaProperties {
        |    "bootstrap.servers": "localhost:9092"
        |    "auto.offset.reset": latest
        |  }
        |  topicsExistenceValidationConfig: {
        |     enabled: true
        |  }
        |}""".stripMargin)
    val expectedConfig = KafkaConfig(Map("bootstrap.servers" -> "localhost:9092", "auto.offset.reset" -> "latest"), None, None, None, TopicsExistenceValidationConfig(enabled = true))
    KafkaConfig.parseConfig(typesafeConfig) shouldEqual expectedConfig
  }

  test("should throw when missing 'bootstrap.servers' property") {
    val typesafeConfig = ConfigFactory.parseString(
      """kafka {
        |  lowLevelComponentsEnabled: false
        |  kafkaProperties {
        |    "auto.offset.reset": latest
        |  }
        |}""".stripMargin)

    the [IllegalArgumentException] thrownBy {
      KafkaConfig.parseConfig(typesafeConfig)
    } should have message "requirement failed: Missing 'bootstrap.servers' property in kafkaProperties"

  }
}
