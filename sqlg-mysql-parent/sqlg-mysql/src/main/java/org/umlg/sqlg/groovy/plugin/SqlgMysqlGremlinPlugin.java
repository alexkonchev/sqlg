package org.umlg.sqlg.groovy.plugin;

import org.apache.tinkerpop.gremlin.jsr223.AbstractGremlinPlugin;
import org.apache.tinkerpop.gremlin.jsr223.DefaultImportCustomizer;
import org.apache.tinkerpop.gremlin.jsr223.ImportCustomizer;
import org.umlg.sqlg.structure.*;
import org.umlg.sqlg.structure.topology.*;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 * Date: 2018/12/03
 */
public class SqlgMysqlGremlinPlugin extends AbstractGremlinPlugin {

    private static final String NAME = "sqlg.mysql";
    private static final ImportCustomizer imports;

    static {
        try {
            imports = DefaultImportCustomizer.build()
                    .addClassImports(
                            PropertyType.class,
                            RecordId.class,
                            SchemaTable.class,
                            SqlgEdge.class,
                            SqlgElement.class,
                            SqlgGraph.class,
                            SqlgProperty.class,
                            SqlgVertex.class,
                            SqlgVertexProperty.class,
                            Topology.class,
                            EdgeLabel.class,
                            VertexLabel.class,
                            Schema.class,
                            PropertyColumn.class,
                            Index.class,
                            IndexType.class,
                            Graph.class
                    )
                    .create();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final SqlgMysqlGremlinPlugin instance = new SqlgMysqlGremlinPlugin();

    public SqlgMysqlGremlinPlugin() {
        super(NAME, imports);
    }

    public static SqlgMysqlGremlinPlugin instance() {
        return instance;
    }

}
