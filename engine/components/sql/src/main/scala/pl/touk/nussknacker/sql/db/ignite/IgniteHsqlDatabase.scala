package pl.touk.nussknacker.sql.db.ignite

import com.typesafe.scalalogging.LazyLogging
import org.hsqldb.jdbc.JDBCDriver
import pl.touk.nussknacker.engine.api.typed.TypedObjectDefinition
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.sql._
import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import scala.util.Using
import scala.util.control.Exception._


/** This class is *not* thread safe. One connection is used to handle all operations
  * the idea is that we prepare all tables, and compile all queries during (lazy) initialization
  * Afterwards query consists of three steps:
  * - insert (in batch) all data
  * - perform query
  * - rollback transaction to reuse connection and not care about deleting/truncating tables
  */
class IgniteHsqlDatabase(query: String, tables: Map[String, IgniteColumnModel]) extends AutoCloseable with LazyLogging {

  import IgniteHsqlDatabase._

  private val connection: Connection = {
    val url = s"jdbc:hsqldb:mem:${UUID.randomUUID()};shutdown=true;allow_empty_batch=true"
    //TODO: sometimes in tests something deregisters HsqlSql driver...
    if (catching(classOf[SQLException]).opt(DriverManager.getDriver(url)).isEmpty) {
      DriverManager.registerDriver(new JDBCDriver)
    }
    DriverManager.getConnection(url, "SA", "")
  }

  init()

  private def init(): Unit = {
    connection.setAutoCommit(false)
    tables.map {
      case (tableName, columnModel) => createTableQuery(tableName, columnModel)
    } foreach { query =>
      logger.debug(query)
      Using.resource(connection.prepareStatement(query))(_.execute())
    }
  }

  private lazy val queryStatement = connection.prepareStatement(query)

  def queryResultsTypeMap: TypedObjectDefinition = {
    val metaData = queryStatement.getMetaData
    toTypedMapDefinition(metaData)
  }

  def parameterMetaData: ParameterMetaData =
    queryStatement.getParameterMetaData

  override def close(): Unit = {
    connection.close()
  }
}

object ClazzToIgniteSqlType extends LazyLogging {
  val STRING: TypingResult = Typed[String]
  val INTEGER: TypingResult = Typed[Int]
  val LONG: TypingResult = Typed[Long]
  val DOUBLE: TypingResult = Typed[Double]
  val BIG_DECIMAL: TypingResult = Typed[BigDecimal]
  val BIG_INT: TypingResult = Typed[BigInt]
  val J_BIG_DECIMAL: TypingResult = Typed[java.math.BigDecimal]
  val J_LONG: TypingResult = Typed[java.lang.Long]
  val J_INTEGER: TypingResult = Typed[java.lang.Integer]
  val J_DOUBLE: TypingResult = Typed[java.lang.Double]
  val J_FLOAT: TypingResult = Typed[java.lang.Float]
  val J_SHORT: TypingResult = Typed[java.lang.Short]
  val J_BYTE: TypingResult = Typed[java.lang.Byte]
  val J_BOOLEAN: TypingResult = Typed[java.lang.Boolean]
  val BOOLEAN: TypingResult = Typed[Boolean]
  val NUMBER: TypingResult = Typed[Number]
  val UUID: TypingResult = Typed[UUID]
  val INSTANT: TypingResult = Typed[Instant]
  val TIME: TypingResult = Typed[LocalTime]
  val LOCAL_DATE_TIME: TypingResult = Typed[LocalDateTime]
  val LOCAL_DATE: TypingResult = Typed[LocalDate]

  def convert(name: String, arg: TypingResult, className: String): Option[IgniteHsqlType] = {
    import IgniteHsqlType._
    arg match {
      case BOOLEAN |
           J_BOOLEAN =>
        Some(Bool)
      case LONG =>
        Some(BigInt)
      case BIG_DECIMAL =>
        Some(Decimal)
      case DOUBLE |
           J_DOUBLE =>
        Some(Double)
      case INTEGER |
           J_INTEGER =>
        Some(Int)
      case J_FLOAT =>
        Some(Real)
      case J_SHORT =>
        Some(SmallInt)
      case J_BYTE =>
        Some(TinyInt)
      case STRING =>
        Some(Varchar)
      case UUID =>
        Some(Uuid)
      case NUMBER =>
        Some(Decimal)
      case LOCAL_DATE_TIME =>
        Some(Date)
      case INSTANT =>
        Some(Timestamp)
      case a =>
        logger.warn(s"No mapping for name: $name in $className and type $a")
        None
    }
  }
}

sealed trait IgniteHsqlType

object IgniteHsqlType {
  // Based on https://ignite.apache.org/docs/latest/sql-reference/data-types
  object Bool extends IgniteHsqlType

  object BigInt extends IgniteHsqlType

  object Decimal extends IgniteHsqlType

  object Double extends IgniteHsqlType

  object Int extends IgniteHsqlType

  object Real extends IgniteHsqlType

  object SmallInt extends IgniteHsqlType

  object TinyInt extends IgniteHsqlType

  object Numeric extends IgniteHsqlType

  object Date extends IgniteHsqlType

  object Timestamp extends IgniteHsqlType

  object Varchar extends IgniteHsqlType

  object Uuid extends IgniteHsqlType
}

case class IgniteColumnModel(columns: List[IgniteColumn])

case class IgniteColumn(name: String, typ: IgniteHsqlType)

private object IgniteHsqlDatabase extends LazyLogging {

  import IgniteHsqlType._

  private val str: IgniteHsqlType => String = {
    case Bool => "BIT"
    case BigInt => "BIGINT"
    case Decimal => "NUMERIC"
    case Double => "REAL"
    case Int => "INT"
    case Real => "REAL"
    case SmallInt => "SMALLINT"
    case TinyInt => "TINYINT"
    case Numeric => "NUMERIC"
    case Date => "DATE"
    case Timestamp => "TIMESTAMP"
    case Varchar => "VARCHAR(50)"
    case Uuid => "VARCHAR(50)"
  }

  private[sql] def createTableQuery(name: String, columnModel: IgniteColumnModel): String = {
    val columns = columnModel.columns
      .map { c =>
        s"${c.name} ${str(c.typ)}"
      }.mkString(", ")
    s"CREATE TABLE $name ($columns)"
  }

  private def toTypedMapDefinition(meta: ResultSetMetaData): TypedObjectDefinition = {
    val cols = (1 to meta.getColumnCount).map { idx =>
      val name = meta.getColumnLabel(idx)
      import ClazzToIgniteSqlType._
      val typ = meta.getColumnType(idx) match {
        case Types.BOOLEAN => BOOLEAN
        case Types.BIGINT => BIG_INT
        case Types.REAL => BIG_DECIMAL
        case Types.INTEGER => INTEGER
        case Types.SMALLINT => INTEGER
        case Types.TINYINT => INTEGER
        case Types.NUMERIC => BIG_DECIMAL
        case Types.DATE => LOCAL_DATE
        case Types.TIMESTAMP => INSTANT
        case Types.VARCHAR => STRING
        case a =>
          logger.warn(s"no type mapping for column type: $a, column: ${meta.getColumnName(idx)}")
          Typed[Any]
      }
      name -> typ
    }.toList

    TypedObjectDefinition(cols)
  }
}

