package org.umlg.sqlg.test.schema;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.*;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.test.BaseTest;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.net.URL;

/**
 * Date: 2014/11/03
 * Time: 6:22 PM
 */
public class TestLazyLoadSchema extends BaseTest {

    @BeforeClass
    public static void beforeClass() throws ClassNotFoundException, IOException, PropertyVetoException {
        URL sqlProperties = Thread.currentThread().getContextClassLoader().getResource("sqlg.properties");
        try {
            configuration = new PropertiesConfiguration(sqlProperties);
            Assume.assumeTrue(configuration.getString("jdbc.url").contains("postgresql"));
            configuration.addProperty("distributed", true);
            if (!configuration.containsKey("jdbc.url"))
                throw new IllegalArgumentException(String.format("SqlGraph configuration requires that the %s be set", "jdbc.url"));

        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLazyLoadTableViaVertexHas() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            this.sqlgGraph.tx().commit();
            //postgresql notify only happens after the commit, need to wait a bit.
            Thread.sleep(1000);
            Assert.assertEquals(1, sqlgGraph1.traversal().V().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLazyLoadTableViaVertexHasWithKey() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            this.sqlgGraph.tx().commit();
            //Not entirely sure what this is for, else it seems hazelcast existVertexLabel not yet distributed the map
            Thread.sleep(1000);

            Assert.assertEquals(1, sqlgGraph1.traversal().V().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").has("name", "a").count().next().intValue());
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLazyLoadTableViaVertexHasWithKeyMissingColumn() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            this.sqlgGraph.tx().commit();
            //Not entirely sure what this is for, else it seems hazelcast existVertexLabel not yet distributed the map
            Thread.sleep(1000);

            Assert.assertEquals(1, sqlgGraph1.traversal().V().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").has("name", "a").count().next().intValue());
            Vertex v11 = sqlgGraph1.traversal().V().has(T.label, "Person").<Vertex>has("name", "a").next();
            Assert.assertFalse(v11.property("surname").isPresent());
            //the next alter will lock if this transaction is still active
            sqlgGraph1.tx().rollback();

            //add column in one
            v1.property("surname", "bbb");
            this.sqlgGraph.tx().commit();

            Thread.sleep(1_000);

            Vertex v12 = sqlgGraph1.addVertex(T.label, "Person", "surname", "ccc");
            Assert.assertEquals("ccc", v12.value("surname"));
            sqlgGraph1.tx().rollback();
        }
    }

    //Fails via maven for Hsqldb
    @Test
    public void testLazyLoadTableViaEdgeCreation() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex v2 = this.sqlgGraph.addVertex(T.label, "Person", "name", "b");
            this.sqlgGraph.tx().commit();
            Thread.sleep(1000);
            Vertex v11 = sqlgGraph1.addVertex(T.label, "Person", "surname", "ccc");

            Vertex v12 = sqlgGraph1.addVertex(T.label, "Person", "surname", "ccc");
            sqlgGraph1.tx().commit();

            v1.addEdge("friend", v2);
            this.sqlgGraph.tx().commit();

            v11.addEdge("friend", v12);
            sqlgGraph1.tx().commit();

            Assert.assertEquals(1, vertexTraversal(v11).out("friend").count().next().intValue());
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLazyLoadTableViaEdgesHas() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            //add a vertex in the old, the new should only see it after a commit
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex v2 = this.sqlgGraph.addVertex(T.label, "Person", "name", "b");
            v1.addEdge("friend", v2);
            this.sqlgGraph.tx().commit();
            //Not entirely sure what this is for, else it seems hazelcast existVertexLabel not yet distributed the map
            Thread.sleep(1000);

            Assert.assertEquals(1, this.sqlgGraph.traversal().E().has(T.label, "friend").count().next().intValue());

            Assert.assertEquals(1, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "friend").count().next().intValue());
            Assert.assertEquals(2, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLoadSchemaRemembersUncommittedSchemas() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Vertex v1 = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex v2 = this.sqlgGraph.addVertex(T.label, "Person", "name", "b");
            v1.addEdge("friend", v2);
            this.sqlgGraph.tx().commit();
            Vertex v3 = this.sqlgGraph.addVertex(T.label, "Animal", "name", "b");
            Vertex v4 = this.sqlgGraph.addVertex(T.label, "Car", "name", "b");
            Assert.assertEquals(1, this.sqlgGraph.traversal().E().has(T.label, "friend").count().next().intValue());
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1000);

            Assert.assertEquals(1, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "friend").count().next().intValue());
            Assert.assertEquals(2, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());

            sqlgGraph1.tx().rollback();
        }
    }

    @Test
    public void testLoadSchemaEdge() throws Exception {
        //Create a new sqlgGraph
        try (SqlgGraph sqlgGraph1 = SqlgGraph.open(configuration)) {
            Vertex personVertex = this.sqlgGraph.addVertex(T.label, "Person", "name", "a");
            Vertex dogVertex = this.sqlgGraph.addVertex(T.label, "Dog", "name", "b");
            Edge petEdge = personVertex.addEdge("pet", dogVertex);
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);

            Assert.assertEquals(1, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "pet").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Dog").count().next().intValue());

            Assert.assertEquals(dogVertex, sqlgGraph1.traversal().V(personVertex.id()).out("pet").next());
            Assert.assertEquals(personVertex, sqlgGraph1.traversal().V(dogVertex.id()).in("pet").next());
            Assert.assertEquals("a", sqlgGraph1.traversal().V(personVertex.id()).next().<String>value("name"));
            Assert.assertEquals("b", sqlgGraph1.traversal().V(dogVertex.id()).next().<String>value("name"));

            sqlgGraph1.tx().rollback();

            //add a property to the vertex
            personVertex.property("surname", "AAA");
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);
            Assert.assertEquals("AAA", sqlgGraph1.traversal().V(personVertex.id()).next().<String>value("surname"));

            //add property to the edge
            petEdge.property("edgeProperty1", "a");
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);
            Assert.assertEquals("a", sqlgGraph1.traversal().E(petEdge.id()).next().<String>value("edgeProperty1"));

            //add an edge
            Vertex addressVertex = this.sqlgGraph.addVertex(T.label, "Address", "name", "1 Nowhere");
            personVertex.addEdge("homeAddress", addressVertex);
            this.sqlgGraph.tx().commit();

            //allow time for notification to happen
            Thread.sleep(1_000);
            Assert.assertEquals(2, sqlgGraph1.traversal().E().count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "pet").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().E().has(T.label, "homeAddress").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Person").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Dog").count().next().intValue());
            Assert.assertEquals(1, sqlgGraph1.traversal().V().has(T.label, "Address").count().next().intValue());

        }
    }

}
