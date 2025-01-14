/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package pl.touk.nussknacker.engine.schemedkafka.schema;

import org.apache.avro.specific.SpecificData;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class GeneratedAvroClassWithLogicalTypes extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 3881014738167665372L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"GeneratedAvroClassWithLogicalTypes\",\"namespace\":\"pl.touk.nussknacker.engine.schemedkafka.schema\",\"fields\":[{\"name\":\"text\",\"type\":\"string\",\"default\":\"123\"},{\"name\":\"dateTime\",\"type\":[\"null\",{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}],\"default\":null},{\"name\":\"date\",\"type\":[\"null\",{\"type\":\"int\",\"logicalType\":\"date\"}],\"default\":null},{\"name\":\"time\",\"type\":[\"null\",{\"type\":\"int\",\"logicalType\":\"time-millis\"}],\"default\":null},{\"name\":\"decimal\",\"type\":[\"null\",{\"type\":\"bytes\",\"logicalType\":\"decimal\",\"precision\":4,\"scale\":2}],\"default\":null}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();
static {
    MODEL$.addLogicalTypeConversion(new org.apache.avro.data.TimeConversions.DateConversion());
    MODEL$.addLogicalTypeConversion(new org.apache.avro.data.TimeConversions.TimestampMillisConversion());
    MODEL$.addLogicalTypeConversion(new org.apache.avro.Conversions.DecimalConversion());
    MODEL$.addLogicalTypeConversion(new org.apache.avro.data.TimeConversions.TimeMillisConversion());
  }

  private static final BinaryMessageEncoder<GeneratedAvroClassWithLogicalTypes> ENCODER =
      new BinaryMessageEncoder<GeneratedAvroClassWithLogicalTypes>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<GeneratedAvroClassWithLogicalTypes> DECODER =
      new BinaryMessageDecoder<GeneratedAvroClassWithLogicalTypes>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<GeneratedAvroClassWithLogicalTypes> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<GeneratedAvroClassWithLogicalTypes> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<GeneratedAvroClassWithLogicalTypes> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<GeneratedAvroClassWithLogicalTypes>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this GeneratedAvroClassWithLogicalTypes to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a GeneratedAvroClassWithLogicalTypes from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a GeneratedAvroClassWithLogicalTypes instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static GeneratedAvroClassWithLogicalTypes fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  @Deprecated public java.lang.CharSequence text;
  @Deprecated public java.time.Instant dateTime;
  @Deprecated public java.time.LocalDate date;
  @Deprecated public java.time.LocalTime time;
  @Deprecated public java.math.BigDecimal decimal;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public GeneratedAvroClassWithLogicalTypes() {}

  /**
   * All-args constructor.
   * @param text The new value for text
   * @param dateTime The new value for dateTime
   * @param date The new value for date
   * @param time The new value for time
   * @param decimal The new value for decimal
   */
  public GeneratedAvroClassWithLogicalTypes(java.lang.CharSequence text, java.time.Instant dateTime, java.time.LocalDate date, java.time.LocalTime time, java.math.BigDecimal decimal) {
    this.text = text;
    this.dateTime = dateTime;
    this.date = date;
    this.time = time;
    this.decimal = decimal;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return text;
    case 1: return dateTime;
    case 2: return date;
    case 3: return time;
    case 4: return decimal;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: text = (java.lang.CharSequence)value$; break;
    case 1: dateTime = (java.time.Instant)value$; break;
    case 2: date = (java.time.LocalDate)value$; break;
    case 3: time = (java.time.LocalTime)value$; break;
    case 4: decimal = (java.math.BigDecimal)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'text' field.
   * @return The value of the 'text' field.
   */
  public java.lang.CharSequence getText() {
    return text;
  }


  /**
   * Sets the value of the 'text' field.
   * @param value the value to set.
   */
  public void setText(java.lang.CharSequence value) {
    this.text = value;
  }

  /**
   * Gets the value of the 'dateTime' field.
   * @return The value of the 'dateTime' field.
   */
  public java.time.Instant getDateTime() {
    return dateTime;
  }


  /**
   * Sets the value of the 'dateTime' field.
   * @param value the value to set.
   */
  public void setDateTime(java.time.Instant value) {
    this.dateTime = value;
  }

  /**
   * Gets the value of the 'date' field.
   * @return The value of the 'date' field.
   */
  public java.time.LocalDate getDate() {
    return date;
  }


  /**
   * Sets the value of the 'date' field.
   * @param value the value to set.
   */
  public void setDate(java.time.LocalDate value) {
    this.date = value;
  }

  /**
   * Gets the value of the 'time' field.
   * @return The value of the 'time' field.
   */
  public java.time.LocalTime getTime() {
    return time;
  }


  /**
   * Sets the value of the 'time' field.
   * @param value the value to set.
   */
  public void setTime(java.time.LocalTime value) {
    this.time = value;
  }

  /**
   * Gets the value of the 'decimal' field.
   * @return The value of the 'decimal' field.
   */
  public java.math.BigDecimal getDecimal() {
    return decimal;
  }


  /**
   * Sets the value of the 'decimal' field.
   * @param value the value to set.
   */
  public void setDecimal(java.math.BigDecimal value) {
    this.decimal = value;
  }

  /**
   * Creates a new GeneratedAvroClassWithLogicalTypes RecordBuilder.
   * @return A new GeneratedAvroClassWithLogicalTypes RecordBuilder
   */
  public static pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder newBuilder() {
    return new pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder();
  }

  /**
   * Creates a new GeneratedAvroClassWithLogicalTypes RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new GeneratedAvroClassWithLogicalTypes RecordBuilder
   */
  public static pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder newBuilder(pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder other) {
    if (other == null) {
      return new pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder();
    } else {
      return new pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder(other);
    }
  }

  /**
   * Creates a new GeneratedAvroClassWithLogicalTypes RecordBuilder by copying an existing GeneratedAvroClassWithLogicalTypes instance.
   * @param other The existing instance to copy.
   * @return A new GeneratedAvroClassWithLogicalTypes RecordBuilder
   */
  public static pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder newBuilder(pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes other) {
    if (other == null) {
      return new pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder();
    } else {
      return new pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder(other);
    }
  }

  /**
   * RecordBuilder for GeneratedAvroClassWithLogicalTypes instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<GeneratedAvroClassWithLogicalTypes>
    implements org.apache.avro.data.RecordBuilder<GeneratedAvroClassWithLogicalTypes> {

    private java.lang.CharSequence text;
    private java.time.Instant dateTime;
    private java.time.LocalDate date;
    private java.time.LocalTime time;
    private java.math.BigDecimal decimal;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.text)) {
        this.text = data().deepCopy(fields()[0].schema(), other.text);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.dateTime)) {
        this.dateTime = data().deepCopy(fields()[1].schema(), other.dateTime);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (isValidValue(fields()[2], other.date)) {
        this.date = data().deepCopy(fields()[2].schema(), other.date);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
      if (isValidValue(fields()[3], other.time)) {
        this.time = data().deepCopy(fields()[3].schema(), other.time);
        fieldSetFlags()[3] = other.fieldSetFlags()[3];
      }
      if (isValidValue(fields()[4], other.decimal)) {
        this.decimal = data().deepCopy(fields()[4].schema(), other.decimal);
        fieldSetFlags()[4] = other.fieldSetFlags()[4];
      }
    }

    /**
     * Creates a Builder by copying an existing GeneratedAvroClassWithLogicalTypes instance
     * @param other The existing instance to copy.
     */
    private Builder(pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.text)) {
        this.text = data().deepCopy(fields()[0].schema(), other.text);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.dateTime)) {
        this.dateTime = data().deepCopy(fields()[1].schema(), other.dateTime);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.date)) {
        this.date = data().deepCopy(fields()[2].schema(), other.date);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.time)) {
        this.time = data().deepCopy(fields()[3].schema(), other.time);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.decimal)) {
        this.decimal = data().deepCopy(fields()[4].schema(), other.decimal);
        fieldSetFlags()[4] = true;
      }
    }

    /**
      * Gets the value of the 'text' field.
      * @return The value.
      */
    public java.lang.CharSequence getText() {
      return text;
    }


    /**
      * Sets the value of the 'text' field.
      * @param value The value of 'text'.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder setText(java.lang.CharSequence value) {
      validate(fields()[0], value);
      this.text = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'text' field has been set.
      * @return True if the 'text' field has been set, false otherwise.
      */
    public boolean hasText() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'text' field.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder clearText() {
      text = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'dateTime' field.
      * @return The value.
      */
    public java.time.Instant getDateTime() {
      return dateTime;
    }


    /**
      * Sets the value of the 'dateTime' field.
      * @param value The value of 'dateTime'.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder setDateTime(java.time.Instant value) {
      validate(fields()[1], value);
      this.dateTime = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'dateTime' field has been set.
      * @return True if the 'dateTime' field has been set, false otherwise.
      */
    public boolean hasDateTime() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'dateTime' field.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder clearDateTime() {
      dateTime = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'date' field.
      * @return The value.
      */
    public java.time.LocalDate getDate() {
      return date;
    }


    /**
      * Sets the value of the 'date' field.
      * @param value The value of 'date'.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder setDate(java.time.LocalDate value) {
      validate(fields()[2], value);
      this.date = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'date' field has been set.
      * @return True if the 'date' field has been set, false otherwise.
      */
    public boolean hasDate() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'date' field.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder clearDate() {
      date = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /**
      * Gets the value of the 'time' field.
      * @return The value.
      */
    public java.time.LocalTime getTime() {
      return time;
    }


    /**
      * Sets the value of the 'time' field.
      * @param value The value of 'time'.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder setTime(java.time.LocalTime value) {
      validate(fields()[3], value);
      this.time = value;
      fieldSetFlags()[3] = true;
      return this;
    }

    /**
      * Checks whether the 'time' field has been set.
      * @return True if the 'time' field has been set, false otherwise.
      */
    public boolean hasTime() {
      return fieldSetFlags()[3];
    }


    /**
      * Clears the value of the 'time' field.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder clearTime() {
      time = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /**
      * Gets the value of the 'decimal' field.
      * @return The value.
      */
    public java.math.BigDecimal getDecimal() {
      return decimal;
    }


    /**
      * Sets the value of the 'decimal' field.
      * @param value The value of 'decimal'.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder setDecimal(java.math.BigDecimal value) {
      validate(fields()[4], value);
      this.decimal = value;
      fieldSetFlags()[4] = true;
      return this;
    }

    /**
      * Checks whether the 'decimal' field has been set.
      * @return True if the 'decimal' field has been set, false otherwise.
      */
    public boolean hasDecimal() {
      return fieldSetFlags()[4];
    }


    /**
      * Clears the value of the 'decimal' field.
      * @return This builder.
      */
    public pl.touk.nussknacker.engine.schemedkafka.schema.GeneratedAvroClassWithLogicalTypes.Builder clearDecimal() {
      decimal = null;
      fieldSetFlags()[4] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GeneratedAvroClassWithLogicalTypes build() {
      try {
        GeneratedAvroClassWithLogicalTypes record = new GeneratedAvroClassWithLogicalTypes();
        record.text = fieldSetFlags()[0] ? this.text : (java.lang.CharSequence) defaultValue(fields()[0]);
        record.dateTime = fieldSetFlags()[1] ? this.dateTime : (java.time.Instant) defaultValue(fields()[1]);
        record.date = fieldSetFlags()[2] ? this.date : (java.time.LocalDate) defaultValue(fields()[2]);
        record.time = fieldSetFlags()[3] ? this.time : (java.time.LocalTime) defaultValue(fields()[3]);
        record.decimal = fieldSetFlags()[4] ? this.decimal : (java.math.BigDecimal) defaultValue(fields()[4]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<GeneratedAvroClassWithLogicalTypes>
    WRITER$ = (org.apache.avro.io.DatumWriter<GeneratedAvroClassWithLogicalTypes>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<GeneratedAvroClassWithLogicalTypes>
    READER$ = (org.apache.avro.io.DatumReader<GeneratedAvroClassWithLogicalTypes>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}