package node;

import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.HighlyAvailableGraphDatabaseFactory;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.server.WrappingNeoServerBootstrapper;
import org.neo4j.server.configuration.ServerConfigurator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Neo4jWrapper {
  public class Result {
    public String[] columnNames;
    public Object[][] result;
    public Result(String[] columnNames, Object[][] result) {
      this.columnNames = columnNames;
      this.result = result;
    }
  }

  public class Property {
    public String name;
    public Object value;
    public Property(String name, Object value) {
      this.name = name;
      this.value = value;
    }
  }
  public GraphDatabaseService connect(String dir, Map<String, String> properties) {
    GraphDatabaseService graphDb = new GraphDatabaseFactory()
        .newEmbeddedDatabaseBuilder(dir)
        .setConfig(properties)
        .newGraphDatabase();
    this.installShutdownHook(graphDb);
    return graphDb;
  }
  public GraphDatabaseService connectHA(String dir, Map<String, String> haConfig, Map<String, Object> serverProperties) {
    GraphDatabaseAPI graphDb = (GraphDatabaseAPI) new HighlyAvailableGraphDatabaseFactory()
      .newHighlyAvailableDatabaseBuilder(dir)
      .setConfig(haConfig)
      .newGraphDatabase();

    ServerConfigurator config = new ServerConfigurator( graphDb );
    Iterator<String> iterator = serverProperties.keySet().iterator();
    while(iterator.hasNext()) {
      String key = iterator.next();
      config.configuration().setProperty(key, serverProperties.get(key));
    }

    this.installShutdownHook(graphDb);

    new WrappingNeoServerBootstrapper( graphDb, config ).start();

    return graphDb;
  }
  public GraphDatabaseService connectWrapped(String dir, Map<String, String> properties, Map<String, Object> serverProperties) {
    GraphDatabaseAPI graphDb = (GraphDatabaseAPI) this.connect(dir, properties);

    ServerConfigurator config = new ServerConfigurator( graphDb );
    Iterator<String> iterator = serverProperties.keySet().iterator();
    while(iterator.hasNext()) {
      String key = iterator.next();
      config.configuration().setProperty(key, serverProperties.get(key));
    }

    new WrappingNeoServerBootstrapper( graphDb, config ).start();

    return graphDb;
  }

  public void installShutdownHook(final GraphDatabaseService graphDb) {
    Runtime.getRuntime().addShutdownHook( new Thread()
    {
      @Override
      public void run()
      {
        graphDb.shutdown();
      }
    } );
  }

  public Property[] getNodeProperties(Node node) {
    ArrayList<Property> properties = new ArrayList<Property>();
    Iterator<String> iterator = node.getPropertyKeys().iterator();
    while(iterator.hasNext()) {
      String key = iterator.next();
      properties.add(new Property(key, node.getProperty(key)));
    }
    return properties.toArray(new Property[properties.size()]);
  }
  public String getType(Relationship relationship) {
    return relationship.getType().name();
  }
  public Result query(GraphDatabaseService graphDb, String query, Map<String, Object> params) {
    ArrayList<Object[]> results = new ArrayList<Object[]>();
    ArrayList<String> columnNames = new ArrayList<String>();
    ExecutionEngine engine = new ExecutionEngine(graphDb);
    ExecutionResult result = engine.execute(query, params);

    Boolean firstRow = true;
    for(Map<String, Object> row : result) {
      ArrayList<Object> rowResult = new ArrayList<Object>();
      for(Map.Entry<String, Object> column : row.entrySet()) {
        if(firstRow) columnNames.add(column.getKey());
        rowResult.add(column.getValue());
      }
      results.add(rowResult.toArray(new Object[rowResult.size()]));
      firstRow = false;
    }

    return new Result(
      columnNames.toArray(new String[columnNames.size()]),
      results.toArray(new Object[results.size()][])
    );
  }

  public static void main(String[] args) {
    HashMap<String, String> haConfig = new HashMap<String, String>();

    haConfig.put("ha.server_id", "3");
    haConfig.put("ha.initial_hosts", ":5001,:5002,:5003");
    haConfig.put("ha.server", ":6003");
    haConfig.put("ha.cluster_server", ":5003");
    haConfig.put("org.neo4j.server.database.mode", "HA");

    final GraphDatabaseService graphDb = new HighlyAvailableGraphDatabaseFactory()
      .newHighlyAvailableDatabaseBuilder("test.db")
      .setConfig(haConfig)
      .newGraphDatabase();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        graphDb.shutdown();
      }
    });
  }
}