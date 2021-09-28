package pl.touk.nussknacker.sql.db.ignite

import com.typesafe.scalalogging.LazyLogging
import org.hsqldb.jdbc.JDBCDriver
import pl.touk.nussknacker.engine.api.typed.TypedObjectDefinition
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.sql.{ColumnModel, SqlType}

import java.sql._
import java.util.UUID
import scala.util.Using
import scala.util.control.Exception._


/** This class is *not* thread safe. One connection is used to handle all operations
  the idea is that we prepare all tables, and compile all queries during (lazy) initialization
  Afterwards query consists of three steps:
  - insert (in batch) all data
  - perform query
  - rollback transaction to reuse connection and not care about deleting/truncating tables
  */
class HsqlSqlQueryableDatabase(query: String, tables: Map[String, ColumnModel]) extends AutoCloseable with LazyLogging {

  import HsqlSqlQueryableDatabase._

  private val connection: Connection = {
    val url = s"jdbc:hsqldb:mem:${UUID.randomUUID()};shutdown=true;allow_empty_batch=true"
    //TODO: sometimes in tests something deregisters HsqlSql driver...
    if (catching(classOf[SQLException]).opt( DriverManager.getDriver(url)).isEmpty) {
      DriverManager.registerDriver(new JDBCDriver)
    }
    DriverManager.getConnection(url, "SA", "")
  }

  init()

  private def init(): Unit = {
    connection.setAutoCommit(false)
    tables.map {
      case(tableName, columnModel) => createTableQuery(tableName, columnModel)
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

private object HsqlSqlQueryableDatabase extends LazyLogging {
  import SqlType._
  private val str: SqlType => String = {
    case Numeric => "NUMERIC"
    case Decimal => "DECIMAL(20, 2)"
    case Varchar => "VARCHAR(50)"
    case Bool => "BIT"
    case Date => "DATETIME"
  }

  private[sql] def createTableQuery(name: String, columnModel: ColumnModel): String = {
    val columns = columnModel.columns
      .map { c =>
        s"${c.name} ${str(c.typ)}"
      }.mkString(", ")
    s"CREATE TABLE $name ($columns)"
  }

  private def toTypedMapDefinition(meta: ResultSetMetaData): TypedObjectDefinition = {
    val cols = (1 to meta.getColumnCount).map { idx =>
      val name = meta.getColumnLabel(idx)
      //more mappings?
      val typ = meta.getColumnType(idx) match {
        case Types.BIT | Types.TINYINT | Types.SMALLINT | Types.INTEGER | Types.BIGINT | Types.FLOAT | Types.REAL | Types.DOUBLE | Types.NUMERIC | Types.DECIMAL =>
          Typed[Number]
        case Types.VARCHAR | Types.LONGNVARCHAR =>
          Typed[String]
        case Types.CHAR =>
          Typed[Char]
        case Types.BOOLEAN =>
          Typed[Boolean]
        case Types.DATE | Types.TIMESTAMP =>
          Typed[java.time.Instant]
        case a =>
          logger.warn(s"no type mapping for column type: $a, column: ${meta.getColumnName(idx)}")
          Typed[Any]
      }
      name -> typ
    }.toList

    TypedObjectDefinition(cols)
  }
}

