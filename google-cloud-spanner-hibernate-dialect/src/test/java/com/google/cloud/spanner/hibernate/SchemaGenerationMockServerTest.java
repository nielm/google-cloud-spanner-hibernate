/*
 * Copyright 2019-2023 Google LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */

package com.google.cloud.spanner.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.cloud.spanner.MockSpannerServiceImpl.StatementResult;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.hibernate.entities.Account;
import com.google.cloud.spanner.hibernate.entities.Airplane;
import com.google.cloud.spanner.hibernate.entities.Airport;
import com.google.cloud.spanner.hibernate.entities.AutoIdEntity;
import com.google.cloud.spanner.hibernate.entities.Child;
import com.google.cloud.spanner.hibernate.entities.Customer;
import com.google.cloud.spanner.hibernate.entities.Employee;
import com.google.cloud.spanner.hibernate.entities.EnhancedSequenceEntity;
import com.google.cloud.spanner.hibernate.entities.GrandParent;
import com.google.cloud.spanner.hibernate.entities.Invoice;
import com.google.cloud.spanner.hibernate.entities.LegacySequenceEntity;
import com.google.cloud.spanner.hibernate.entities.Parent;
import com.google.cloud.spanner.hibernate.entities.PooledSequenceEntity;
import com.google.cloud.spanner.hibernate.entities.SequenceEntity;
import com.google.cloud.spanner.hibernate.entities.Singer;
import com.google.cloud.spanner.hibernate.entities.SubTestEntity;
import com.google.cloud.spanner.hibernate.entities.TestEntity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import com.google.spanner.admin.database.v1.UpdateDatabaseDdlRequest;
import com.google.spanner.v1.CommitRequest;
import com.google.spanner.v1.ExecuteBatchDmlRequest;
import com.google.spanner.v1.ExecuteSqlRequest;
import com.google.spanner.v1.ResultSet;
import com.google.spanner.v1.ResultSetMetadata;
import com.google.spanner.v1.StructType;
import com.google.spanner.v1.StructType.Field;
import com.google.spanner.v1.Type;
import com.google.spanner.v1.TypeCode;
import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Environment;
import org.junit.Test;

/**
 * Verifies that the correct database schema is being generated, and that the schema generation uses
 * a DDL batch.
 */
public class SchemaGenerationMockServerTest extends AbstractSchemaGenerationMockServerTest {

  /**
   * Set up empty mocked results for schema queries.
   */
  public static void setupEmptySchemaQueryResults() {
    mockSpanner.putStatementResult(
        StatementResult.query(
            GET_TABLES_STATEMENT,
            ResultSet.newBuilder()
                .setMetadata(GET_TABLES_METADATA)
                .build()));
    mockSpanner.putStatementResult(
        StatementResult.query(
            GET_INDEXES_STATEMENT,
            ResultSet.newBuilder()
                .setMetadata(
                    ResultSetMetadata.newBuilder()
                        .setRowType(StructType.newBuilder().build())
                        .build())
                .build()));
    mockSpanner.putStatementResult(
        StatementResult.query(GET_COLUMNS_STATEMENT, ResultSet.newBuilder()
            .setMetadata(ResultSetMetadata.newBuilder()
                .setRowType(StructType.newBuilder().build())
                .build())
            .build()));
    mockSpanner.putStatementResult(
        StatementResult.query(GET_SEQUENCES_STATEMENT, ResultSet.newBuilder()
            .setMetadata(GET_SEQUENCES_METADATA)
            .build()));
  }

  @Test
  public void testGenerateSchema() {
    setupEmptySchemaQueryResults();
    addDdlResponseToSpannerAdmin();

    //noinspection EmptyTryBlock
    try (SessionFactory ignore =
        createTestHibernateConfig(
            ImmutableList.of(Singer.class, Invoice.class, Customer.class, Account.class),
            ImmutableMap.of("hibernate.hbm2ddl.auto", "create-only"))
            .buildSessionFactory()) {
      // do nothing, just generate the schema.
    }

    // Check the DDL statements that were generated.
    List<UpdateDatabaseDdlRequest> requests =
        mockDatabaseAdmin.getRequests().stream()
            .filter(request -> request instanceof UpdateDatabaseDdlRequest)
            .map(request -> (UpdateDatabaseDdlRequest) request)
            .collect(Collectors.toList());
    assertEquals(1, requests.size());
    UpdateDatabaseDdlRequest request = requests.get(0);
    assertEquals(8, request.getStatementsCount());

    int index = -1;

    assertEquals(
        "create table Account (id INT64 not null,amount NUMERIC,name STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
    assertEquals(
        "create table Customer (customerId INT64 not null,name STRING(255)) PRIMARY KEY (customerId)",
        request.getStatements(++index));
    assertEquals(
        "create table customerId (next_val INT64) PRIMARY KEY ()", request.getStatements(++index));
    assertEquals(
        "create table Invoice (invoiceId INT64 not null,number STRING(255),customer_customerId INT64) PRIMARY KEY (invoiceId)",
        request.getStatements(++index));
    assertEquals(
        "create table invoiceId (next_val INT64) PRIMARY KEY ()", request.getStatements(++index));
    assertEquals(
        "create table Singer (id INT64 not null) PRIMARY KEY (id)", request.getStatements(++index));
    assertEquals(
        "create table singerId (next_val INT64) PRIMARY KEY ()", request.getStatements(++index));
    assertEquals(
        "alter table Invoice add constraint fk_invoice_customer foreign key (customer_customerId) references Customer (customerId) on delete cascade",
        request.getStatements(++index));
  }

  @Test
  public void testGenerateEmployeeSchema() {
    // Disable sequences for the duration of this test, as it was built with table-backed sequences
    // in mind.
    SpannerDialect.disableSpannerSequences();
    try {
      setupEmptySchemaQueryResults();
      addDdlResponseToSpannerAdmin();

      //noinspection EmptyTryBlock
      try (SessionFactory ignore =
          createTestHibernateConfig(
              ImmutableList.of(Employee.class),
              ImmutableMap.of(
                  "hibernate.hbm2ddl.auto", "create-only",
                  "spanner.disable_sequence_support", "true"))
              .buildSessionFactory()) {
        // do nothing, just generate the schema.
      }

      // Check the DDL statements that were generated.
      List<UpdateDatabaseDdlRequest> requests =
          mockDatabaseAdmin.getRequests().stream()
              .filter(request -> request instanceof UpdateDatabaseDdlRequest)
              .map(request -> (UpdateDatabaseDdlRequest) request)
              .collect(Collectors.toList());
      assertEquals(1, requests.size());
      UpdateDatabaseDdlRequest request = requests.get(0);
      assertEquals(4, request.getStatementsCount());

      int index = -1;

      assertEquals(
          "create table Employee (id INT64 not null,name STRING(255),manager_id INT64) PRIMARY KEY (id)",
          request.getStatements(++index));
      assertEquals(
          "create table hibernate_sequence (next_val INT64) PRIMARY KEY ()",
          request.getStatements(++index));
      assertEquals("create index name_index on Employee (name)", request.getStatements(++index));
      assertEquals(
          "alter table Employee add constraint FKiralam2duuhr33k8a10aoc2t6 foreign key (manager_id) references Employee (id)",
          request.getStatements(++index));
    } finally {
      SpannerDialect.enableSpannerSequences();
    }
  }

  @Test
  public void testGenerateAirportSchema() {
    setupEmptySchemaQueryResults();
    addDdlResponseToSpannerAdmin();

    //noinspection EmptyTryBlock
    try (SessionFactory ignore =
        createTestHibernateConfig(
            ImmutableList.of(Airplane.class, Airport.class),
            ImmutableMap.of("hibernate.hbm2ddl.auto", "create-only"))
            .buildSessionFactory()) {
      // do nothing, just generate the schema.
    }

    // Check the DDL statements that were generated.
    List<UpdateDatabaseDdlRequest> requests =
        mockDatabaseAdmin.getRequests().stream()
            .filter(request -> request instanceof UpdateDatabaseDdlRequest)
            .map(request -> (UpdateDatabaseDdlRequest) request)
            .collect(Collectors.toList());
    assertEquals(1, requests.size());
    UpdateDatabaseDdlRequest request = requests.get(0);
    assertEquals(7, request.getStatementsCount());

    int index = -1;

    assertEquals(
        "create table Airplane (id STRING(255) not null,modelName STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
    assertEquals(
        "create table Airport (id STRING(255) not null) PRIMARY KEY (id)",
        request.getStatements(++index));
    assertEquals(
        "create table Airport_Airplane (Airport_id STRING(255) not null,airplanes_id STRING(255) not null) PRIMARY KEY (Airport_id,airplanes_id)",
        request.getStatements(++index));

    assertEquals(
        "create unique index UK_gc568wb30sampsuirwne5jqgh on Airplane (modelName)",
        request.getStatements(++index));
    assertEquals(
        "create unique index UK_em0lqvwoqdwt29x0b0r010be on Airport_Airplane (airplanes_id)",
        request.getStatements(++index));
    assertEquals(
        "alter table Airport_Airplane add constraint FKkn0enwaxbwk7csf52x0eps73d foreign key (airplanes_id) references Airplane (id)",
        request.getStatements(++index));
    assertEquals(
        "alter table Airport_Airplane add constraint FKh186t28ublke8o13fo4ppogs7 foreign key (Airport_id) references Airport (id)",
        request.getStatements(++index));
  }

  @Test
  public void testGenerateParentChildSchema() {
    SpannerDialect.disableSpannerSequences();
    try {
      setupEmptySchemaQueryResults();
      addDdlResponseToSpannerAdmin();

      //noinspection EmptyTryBlock
      try (SessionFactory ignore =
          createTestHibernateConfig(
              ImmutableList.of(GrandParent.class, Parent.class, Child.class),
              ImmutableMap.of("hibernate.hbm2ddl.auto", "create-only"))
              .buildSessionFactory()) {
        // do nothing, just generate the schema.
      }

      // Check the DDL statements that were generated.
      List<UpdateDatabaseDdlRequest> requests =
          mockDatabaseAdmin.getRequests().stream()
              .filter(request -> request instanceof UpdateDatabaseDdlRequest)
              .map(request -> (UpdateDatabaseDdlRequest) request)
              .collect(Collectors.toList());
      assertEquals(1, requests.size());
      UpdateDatabaseDdlRequest request = requests.get(0);
      assertEquals(4, request.getStatementsCount());

      int index = -1;

      assertEquals(
          "create table GrandParent (grandParentId INT64 not null,name STRING(255)) PRIMARY KEY (grandParentId)",
          request.getStatements(++index));
      assertEquals(
          "create table Parent (grandParentId INT64 not null,parentId INT64 not null,name STRING(255)) "
              + "PRIMARY KEY (grandParentId,parentId), INTERLEAVE IN PARENT GrandParent",
          request.getStatements(++index));
      assertEquals(
          "create table Child (childId INT64 not null,grandParentId INT64 not null,parentId INT64 not null,name STRING(255)) "
              + "PRIMARY KEY (grandParentId,parentId,childId), INTERLEAVE IN PARENT Parent",
          request.getStatements(++index));
      assertEquals(
          "create table hibernate_sequence (next_val INT64) PRIMARY KEY ()",
          request.getStatements(++index));
    } finally {
      SpannerDialect.enableSpannerSequences();
    }
  }

  @Test
  public void testGenerateTestEntitySchema() {
    setupEmptySchemaQueryResults();
    addDdlResponseToSpannerAdmin();

    //noinspection EmptyTryBlock
    try (SessionFactory ignore =
        createTestHibernateConfig(
            ImmutableList.of(TestEntity.class, SubTestEntity.class),
            ImmutableMap.of("hibernate.hbm2ddl.auto", "create-only"))
            .buildSessionFactory()) {
      // do nothing, just generate the schema.
    }

    // Check the DDL statements that were generated.
    List<UpdateDatabaseDdlRequest> requests =
        mockDatabaseAdmin.getRequests().stream()
            .filter(request -> request instanceof UpdateDatabaseDdlRequest)
            .map(request -> (UpdateDatabaseDdlRequest) request)
            .collect(Collectors.toList());
    assertEquals(1, requests.size());
    UpdateDatabaseDdlRequest request = requests.get(0);
    assertEquals(5, request.getStatementsCount());

    int index = -1;

    assertEquals(
        "create table `TestEntity_stringList` ("
            + "`TestEntity_ID1` INT64 not null,"
            + "`TestEntity_id2` STRING(255) not null,"
            + "stringList STRING(255)) "
            + "PRIMARY KEY (`TestEntity_ID1`,`TestEntity_id2`,stringList)",
        request.getStatements(++index));
    assertEquals(
        "create table SubTestEntity (id STRING(255) not null,id1 INT64,id2 STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
    assertEquals(
        "create table `test_table` ("
            + "`ID1` INT64 not null,id2 STRING(255) not null,"
            + "`boolColumn` BOOL,"
            + "longVal INT64 not null,"
            + "stringVal STRING(255)) "
            + "PRIMARY KEY (`ID1`,id2)",
        request.getStatements(++index));
    assertEquals(
        "alter table `TestEntity_stringList` add constraint FK2is6fwy3079dmfhjot09x5och "
            + "foreign key (`TestEntity_ID1`, `TestEntity_id2`) references `test_table` (`ID1`, id2)",
        request.getStatements(++index));
    assertEquals(
        "alter table SubTestEntity add constraint FK45l9js1jvci3yy21exuclnku0 "
            + "foreign key (id1, id2) references `test_table` (`ID1`, id2)",
        request.getStatements(++index));
  }

  @Test
  public void testGenerateSequence() {
    setupEmptySchemaQueryResults();
    addDdlResponseToSpannerAdmin();

    //noinspection EmptyTryBlock
    try (SessionFactory ignore =
        createTestHibernateConfig(
            ImmutableList.of(SequenceEntity.class, AutoIdEntity.class, PooledSequenceEntity.class),
            ImmutableMap.of("hibernate.hbm2ddl.auto", "update"))
            .buildSessionFactory()) {
      // do nothing, just generate the schema.
    }

    // Check the DDL statements that were generated.
    List<UpdateDatabaseDdlRequest> requests =
        mockDatabaseAdmin.getRequests().stream()
            .filter(request -> request instanceof UpdateDatabaseDdlRequest)
            .map(request -> (UpdateDatabaseDdlRequest) request)
            .collect(Collectors.toList());
    assertEquals(1, requests.size());
    UpdateDatabaseDdlRequest request = requests.get(0);
    assertEquals(6, request.getStatementsCount());

    int index = -1;

    assertEquals(
        "create table AutoIdEntity (id INT64 not null,name STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
    // The PooledSequenceEntity uses a pooled sequence. These are not supported by Cloud Spanner.
    // Hibernate therefore creates a table instead.
    assertEquals(
        "create table pooled_sequence (next_val INT64) PRIMARY KEY ()",
        request.getStatements(++index));
    assertEquals(
        "create table PooledSequenceEntity (id INT64 not null,name STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
    assertEquals(
        "create table SequenceEntity (id INT64 not null,name STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
    // This sequence is generated by the entity that has a no-arg @GeneratedValue annotation.
    assertEquals(
        "create sequence hibernate_sequence options(sequence_kind=\"bit_reversed_positive\")",
        request.getStatements(++index));
    assertEquals(
        "create sequence test_sequence options(sequence_kind=\"bit_reversed_positive\")",
        request.getStatements(++index));
  }

  @Test
  public void testGenerateSequenceWithSequencesDisabled() {
    // Verify that we still get a table-backed sequence if we disable sequence support.
    SpannerDialect.disableSpannerSequences();
    
    try {
      setupEmptySchemaQueryResults();
      addDdlResponseToSpannerAdmin();

      //noinspection EmptyTryBlock
      try (SessionFactory ignore =
          createTestHibernateConfig(
              ImmutableList.of(SequenceEntity.class, AutoIdEntity.class),
              ImmutableMap.of("hibernate.hbm2ddl.auto", "update"))
              .buildSessionFactory()) {
        // do nothing, just generate the schema.
      }

      // Check the DDL statements that were generated.
      List<UpdateDatabaseDdlRequest> requests =
          mockDatabaseAdmin.getRequests().stream()
              .filter(request -> request instanceof UpdateDatabaseDdlRequest)
              .map(request -> (UpdateDatabaseDdlRequest) request)
              .collect(Collectors.toList());
      assertEquals(1, requests.size());
      UpdateDatabaseDdlRequest request = requests.get(0);
      assertEquals(4, request.getStatementsCount());

      int index = -1;

      assertEquals(
          "create table AutoIdEntity (id INT64 not null,name STRING(255)) PRIMARY KEY (id)",
          request.getStatements(++index));
      // Sequences have been disabled, so the dialect generates a table that is used for generating
      // sequential values instead. This specific table (emulated sequence) is the most in-efficient
      // way that you could generate primary key values for Cloud Spanner. It uses a single table
      // for all entities, contains one row and one column, and is updated for each insert
      // operation. This behavior is with the introduction of bit-reversed sequences a lot better,
      // as we at least use a sequence instead of a table for that.
      // Still; users should explicitly configure a different type of identifier.
      assertEquals(
          "create table hibernate_sequence (next_val INT64) PRIMARY KEY ()",
          request.getStatements(++index));
      assertEquals(
          "create table SequenceEntity (id INT64 not null,name STRING(255)) PRIMARY KEY (id)",
          request.getStatements(++index));
      assertEquals(
          "create table test_sequence (next_val INT64) PRIMARY KEY ()",
          request.getStatements(++index));
    } finally {
      SpannerDialect.enableSpannerSequences();
    }
  }

  @Test
  public void testBatchedSequenceEntity_CreateOnly() {
    setupEmptySchemaQueryResults();
    addDdlResponseToSpannerAdmin();
    long sequenceBatchSize = 5L;
    String selectSequenceNextVals = "WITH t AS (\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + ")\n"
        + "SELECT n FROM t";
    String insertSql = "insert into EnhancedSequenceEntity (name, id) values (@p1, @p2)";
    mockSpanner.putStatementResult(StatementResult.query(Statement.of(selectSequenceNextVals),
        ResultSet.newBuilder()
            .setMetadata(ResultSetMetadata.newBuilder()
                .setRowType(StructType.newBuilder()
                    .addFields(Field.newBuilder().setName("n")
                        .setType(Type.newBuilder().setCode(TypeCode.INT64).build()).build())
                    .build())
                .build())
            .addAllRows(LongStream.rangeClosed(1L, sequenceBatchSize)
                .mapToObj(id -> ListValue.newBuilder()
                    .addValues(
                        Value.newBuilder().setStringValue(String.valueOf(Long.reverse(id))).build())
                    .build()).collect(Collectors.toList()))
            .build()));
    mockSpanner.putStatementResult(StatementResult.update(Statement.newBuilder(insertSql)
        .bind("p1").to("test1")
        .bind("p2").to(Long.reverse(1L))
        .build(), 1L));
    mockSpanner.putStatementResult(StatementResult.update(Statement.newBuilder(insertSql)
        .bind("p1").to("test2")
        .bind("p2").to(Long.reverse(2L))
        .build(), 1L));
    
    try (SessionFactory sessionFactory =
        createTestHibernateConfig(
            ImmutableList.of(EnhancedSequenceEntity.class, LegacySequenceEntity.class),
            ImmutableMap.of(Environment.HBM2DDL_AUTO, "create-only",
                Environment.STATEMENT_BATCH_SIZE, "50"))
            .buildSessionFactory(); Session session = sessionFactory.openSession()) {
      Transaction transaction = session.beginTransaction();
      assertEquals(Long.reverse(1L), session.save(new EnhancedSequenceEntity("test1")));
      assertEquals(Long.reverse(2L), session.save(new EnhancedSequenceEntity("test2")));
      transaction.commit();
    }

    ExecuteSqlRequest sequenceRequest = mockSpanner.getRequestsOfType(ExecuteSqlRequest.class)
        .stream().filter(request -> request.getSql().equals(selectSequenceNextVals))
        .findFirst().orElse(ExecuteSqlRequest.getDefaultInstance());
    assertTrue(sequenceRequest.hasTransaction());
    assertTrue(sequenceRequest.getTransaction().hasBegin());
    assertTrue(sequenceRequest.getTransaction().getBegin().hasReadWrite());
    assertEquals(1, mockSpanner.countRequestsOfType(ExecuteBatchDmlRequest.class));
    ExecuteBatchDmlRequest insertRequest = mockSpanner.getRequestsOfType(
        ExecuteBatchDmlRequest.class).get(0);
    assertTrue(insertRequest.hasTransaction());
    assertTrue(insertRequest.getTransaction().hasBegin());
    assertTrue(insertRequest.getTransaction().getBegin().hasReadWrite());
    assertEquals(2, insertRequest.getStatementsCount());
    assertEquals(insertSql, insertRequest.getStatements(0).getSql());
    assertEquals(insertSql, insertRequest.getStatements(1).getSql());
    assertEquals(2, mockSpanner.countRequestsOfType(CommitRequest.class));

    // Check the DDL statements that were generated.
    List<UpdateDatabaseDdlRequest> requests =
        mockDatabaseAdmin.getRequests().stream()
            .filter(request -> request instanceof UpdateDatabaseDdlRequest)
            .map(request -> (UpdateDatabaseDdlRequest) request)
            .collect(Collectors.toList());
    assertEquals(1, requests.size());
    UpdateDatabaseDdlRequest request = requests.get(0);
    assertEquals(4, request.getStatementsCount());

    int index = -1;

    assertEquals(
        "create sequence enhanced_sequence options(sequence_kind=\"bit_reversed_positive\", " 
            + "start_with_counter=5000, skip_range_min=1, skip_range_max=1000)",
        request.getStatements(++index));
    assertEquals(
        "create sequence legacy_entity_sequence " 
            + "options(sequence_kind=\"bit_reversed_positive\", start_with_counter=5000, " 
            + "skip_range_min=1, skip_range_max=20000)",
        request.getStatements(++index));
    assertEquals(
        "create table EnhancedSequenceEntity (id INT64 not null,name STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
    assertEquals(
        "create table LegacySequenceEntity (id INT64 not null,name STRING(255)) PRIMARY KEY (id)",
        request.getStatements(++index));
  }

  @Test
  public void testBatchedSequenceEntity_Update() {
    addDdlResponseToSpannerAdmin();

    // Setup schema results.
    mockSpanner.putStatementResult(
        StatementResult.query(GET_TABLES_STATEMENT, ResultSet.newBuilder()
            .setMetadata(GET_TABLES_METADATA)
            .addRows(createTableRow("EnhancedSequenceEntity"))
            .build()));
    mockSpanner.putStatementResult(
        StatementResult.query(GET_SEQUENCES_STATEMENT, ResultSet.newBuilder()
            .setMetadata(GET_SEQUENCES_METADATA)
            .addRows(createSequenceRow("enhanced_sequence"))
            .build()));
    mockSpanner.putStatementResult(StatementResult.query(GET_COLUMNS_STATEMENT, ResultSet.newBuilder()
            .setMetadata(GET_COLUMNS_METADATA)
            .addRows(createColumnRow("EnhancedSequenceEntity", "id", Types.BIGINT, "INT64", 1))
            .addRows(createColumnRow("EnhancedSequenceEntity", "name", Types.NVARCHAR, "STRING(MAX)", 2))
        .build()));
    mockSpanner.putStatementResult(StatementResult.query(GET_INDEXES_STATEMENT, ResultSet.newBuilder()
        .setMetadata(
            ResultSetMetadata.newBuilder()
                .setRowType(StructType.newBuilder().build())
                .build())
        .build()));

    long sequenceBatchSize = 5L;
    String selectSequenceNextVals = "WITH t AS (\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + "\tUNION ALL\n"
        + "\tselect get_next_sequence_value(sequence enhanced_sequence) AS n\n"
        + ")\n"
        + "SELECT n FROM t";
    String insertSql = "insert into EnhancedSequenceEntity (name, id) values (@p1, @p2)";
    mockSpanner.putStatementResult(StatementResult.query(Statement.of(selectSequenceNextVals),
        ResultSet.newBuilder()
            .setMetadata(ResultSetMetadata.newBuilder()
                .setRowType(StructType.newBuilder()
                    .addFields(Field.newBuilder().setName("n")
                        .setType(Type.newBuilder().setCode(TypeCode.INT64).build()).build())
                    .build())
                .build())
            .addAllRows(LongStream.rangeClosed(1L, sequenceBatchSize)
                .mapToObj(id -> ListValue.newBuilder()
                    .addValues(
                        Value.newBuilder().setStringValue(String.valueOf(Long.reverse(id))).build())
                    .build()).collect(Collectors.toList()))
            .build()));
    mockSpanner.putStatementResult(StatementResult.update(Statement.newBuilder(insertSql)
        .bind("p1").to("test1")
        .bind("p2").to(Long.reverse(1L))
        .build(), 1L));
    mockSpanner.putStatementResult(StatementResult.update(Statement.newBuilder(insertSql)
        .bind("p1").to("test2")
        .bind("p2").to(Long.reverse(2L))
        .build(), 1L));

    try (SessionFactory sessionFactory =
        createTestHibernateConfig(
            ImmutableList.of(EnhancedSequenceEntity.class),
            ImmutableMap.of(Environment.HBM2DDL_AUTO, "update",
                Environment.STATEMENT_BATCH_SIZE, "50"))
            .buildSessionFactory(); Session session = sessionFactory.openSession()) {
      Transaction transaction = session.beginTransaction();
      assertEquals(Long.reverse(1L), session.save(new EnhancedSequenceEntity("test1")));
      assertEquals(Long.reverse(2L), session.save(new EnhancedSequenceEntity("test2")));
      transaction.commit();
    }

    ExecuteSqlRequest sequenceRequest = mockSpanner.getRequestsOfType(ExecuteSqlRequest.class)
        .stream().filter(request -> request.getSql().equals(selectSequenceNextVals))
        .findFirst().orElse(ExecuteSqlRequest.getDefaultInstance());
    assertTrue(sequenceRequest.hasTransaction());
    assertTrue(sequenceRequest.getTransaction().hasBegin());
    assertTrue(sequenceRequest.getTransaction().getBegin().hasReadWrite());
    // Note the existence of an ExecuteBatchDml request. This verifies that our bit-reversed
    // sequence generator supports batch inserts.
    assertEquals(1, mockSpanner.countRequestsOfType(ExecuteBatchDmlRequest.class));
    ExecuteBatchDmlRequest insertRequest = mockSpanner.getRequestsOfType(
        ExecuteBatchDmlRequest.class).get(0);
    assertTrue(insertRequest.hasTransaction());
    assertTrue(insertRequest.getTransaction().hasBegin());
    assertTrue(insertRequest.getTransaction().getBegin().hasReadWrite());
    assertEquals(2, insertRequest.getStatementsCount());
    assertEquals(insertSql, insertRequest.getStatements(0).getSql());
    assertEquals(insertSql, insertRequest.getStatements(1).getSql());
    assertEquals(2, mockSpanner.countRequestsOfType(CommitRequest.class));

    // Check that there were no DDL statements generated as the data model is up-to-date.
    List<UpdateDatabaseDdlRequest> requests =
        mockDatabaseAdmin.getRequests().stream()
            .filter(request -> request instanceof UpdateDatabaseDdlRequest)
            .map(request -> (UpdateDatabaseDdlRequest) request)
            .collect(Collectors.toList());
    assertEquals(0, requests.size());
  }

}
