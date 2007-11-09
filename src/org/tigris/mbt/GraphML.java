package org.tigris.mbt;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.impl.DirectedSparseEdge;
import edu.uci.ics.jung.graph.impl.SparseGraph;
import edu.uci.ics.jung.graph.impl.DirectedSparseVertex;
import edu.uci.ics.jung.utils.Pair;
import edu.uci.ics.jung.utils.UserData;

/**
 * A file reader for GraphML files. The reader can read one single graphml file
 * or several in a single folder. When several files are read, they will be
 * merged. Regardless if one or several files are read, they all end up into
 * one single SparseGraph object.<br><br>
 * <strong>Example: Single graphml file</strong><br>
 * GraphML graphML = new GraphML();<br>
 * SparseGraph graph = graphML.load( "graph.graphml" );<br><br>
 * <strong>Example: Folder containing several graphml files</strong><br>
 * GraphML graphML = new GraphML();<br>
 * SparseGraph graph = graphML.load( "/home/user/graphml_folder/" );<br>
 *
 */
public class GraphML extends AbstractModelHandler
{
	/**
	* Indicator if the graph list needs to be merged 
	*/
	private boolean merged;

	/**
	* List of parsed graphs
	*/
	private java.util.Vector parsedGraphList;
	
	/**
	* A counter for creating unique indexes for edges and vertices.
	*/
	private int	vertexAndEdgeIndex;
	
	/**
	* The logger
	*/
	private Logger logger;

	/**
	* Default constructor. Initializes the default logger.
	*/
	public GraphML()
	{	
		logger = Util.setupLogger( GraphML.class );
		
		parsedGraphList = new Vector();
		vertexAndEdgeIndex = 0;
	}
	
	/**
	* Reads one single graph, or a folder containing several graphs to be merged into one
	* graph.
	*/
	public void load( String fileOrfolder )
	{
		if( fileOrfolder != "" )
		{
			File file = new File( fileOrfolder );
			if ( file.isFile() )
			{
		    	parsedGraphList.add( parseFile( fileOrfolder ) );
				setMerged(false);
			}
			else if ( file.isDirectory() )
			{
			    // Only accepts files which suffix is .graphml
			    FilenameFilter graphmlFilter = new FilenameFilter()
			    {
			        public boolean accept( File dir, String name )
			        {
			            return name.endsWith( ".graphml" );
			        }
			    };
	
			    File [] allChildren = file.listFiles( graphmlFilter );
			    for ( int i = 0; i < allChildren.length; ++i )
			    {
			    	parsedGraphList.add( parseFile( allChildren[ i ].getAbsolutePath() ) );
					setMerged(false);
			    }
			}
			else
			{
				throw new RuntimeException( "'" + fileOrfolder + "' is not a file or a directory. Please specify a valid .graphml file or a directory containing .graphml files" );
			}
		}
		mergeAllGraphs();
	}
	
	/**
	 * Parses the graphml file, and returns the model as a edu.uci.ics.jung.graph.impl.SparseGraph  
	 */
	private SparseGraph parseFile( String fileName )
	{
		SparseGraph graph = new SparseGraph();
		graph.addUserDatum( Keywords.FILE_KEY, fileName, UserData.SHARED );
		SAXBuilder parser = new SAXBuilder( "org.apache.crimson.parser.XMLReaderImpl", false );		
				
		try
		{
			logger.debug( "Parsing file: " + fileName );
			Document doc = parser.build( fileName );

			// Parse all vertices (nodes)
			Iterator iter_node = doc.getDescendants( new org.jdom.filter.ElementFilter( "node" ) );
			while ( iter_node.hasNext() )
			{
				Object o = iter_node.next();
				if ( o instanceof org.jdom.Element )
				{
					org.jdom.Element element = (org.jdom.Element)o;
					if ( element.getAttributeValue( "yfiles.foldertype" ) != null )
					{
						logger.debug( "  Excluded node: " + element.getAttributeValue( "yfiles.foldertype" ) );
						continue;
					}
					Iterator iterUMLNoteIter = element.getDescendants( new org.jdom.filter.ElementFilter( "UMLNoteNode" ) );
					if ( iterUMLNoteIter.hasNext() )
					{
						logger.debug( "  Excluded node: UMLNoteNode" );
						continue;
					}
					logger.debug( "  id: " + element.getAttributeValue( "id" ) );

					// Used to remember which vertex to store the image location.
					DirectedSparseVertex currentVertex = null;
					
					Iterator iterNodeLabel = element.getDescendants( new org.jdom.filter.ElementFilter( "NodeLabel" ) );
					while ( iterNodeLabel.hasNext() )
					{
						Object o2 = iterNodeLabel.next();
						if ( o2 instanceof org.jdom.Element )
						{
							org.jdom.Element nodeLabel = (org.jdom.Element)o2;
							logger.debug( "  Full name: '" + nodeLabel.getQualifiedName() + "'" );
							logger.debug( "  Name: '" + nodeLabel.getTextTrim() + "'" );

							DirectedSparseVertex v = (DirectedSparseVertex) graph.addVertex( new DirectedSparseVertex() );
							currentVertex = v;

							v.addUserDatum( Keywords.ID_KEY, 	 	element.getAttributeValue( "id" ),         UserData.SHARED );
							v.addUserDatum( Keywords.VISITED_KEY, 	new Integer( 0 ), 				           UserData.SHARED );
							v.addUserDatum( Keywords.FILE_KEY, 	 	fileName, 						   		   UserData.SHARED );
							v.addUserDatum( Keywords.FULL_LABEL_KEY, nodeLabel.getTextTrim(), 		           UserData.SHARED );
							v.addUserDatum( Keywords.INDEX_KEY,      new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );

							String str = nodeLabel.getTextTrim();
							Pattern p = Pattern.compile( "(.*)", Pattern.MULTILINE );
							Matcher m = p.matcher( str );
							String label;
							if ( m.find() )
							{
								label = m.group( 1 );
								if ( label.length() <= 0 )
								{
									throw new RuntimeException( "Vertex is missing its label in file: '" + fileName + "'" );
								}
								if ( label.matches( ".*[\\s].*" ) )
								{
									throw new RuntimeException( "Vertex has a label '" + label  + "', containing whitespaces in file: '" + fileName + "'" );
								}
								if ( Keywords.isKeyWord( label ) )
								{
									throw new RuntimeException( "Vertex has a label '" + label  + "', which is a reserved keyword, in file: '" + fileName + "'" );
								}
								v.addUserDatum( Keywords.LABEL_KEY, label, UserData.SHARED );
							}
							else
							{
								throw new RuntimeException( "Label must be defined in file: '" + fileName + "'" );
							}
							logger.debug( "  Added vertex: '" + v.getUserDatum( Keywords.LABEL_KEY ) + "', with id: " + v.getUserDatum( Keywords.INDEX_KEY ) );





							// If merge is defined, find it...
							// If defined, it means that the node will be merged with all other nodes wit the same name,
							// but not replaced by any subgraphs
							p = Pattern.compile( "\\n(MERGE)", Pattern.MULTILINE );
							m = p.matcher( str );
							if ( m.find() )
							{
								v.addUserDatum( Keywords.MERGE, m.group( 1 ), UserData.SHARED );
								logger.debug( "  Found MERGE for vertex: " + label );
							}



							// If no merge is defined, find it...
							// If defined, it means that when merging graphs, this specific vertex will not be merged
							// or replaced by any subgraphs
							p = Pattern.compile( "\\n(NO_MERGE)", Pattern.MULTILINE );
							m = p.matcher( str );
							if ( m.find() )
							{
								v.addUserDatum( Keywords.NO_MERGE, m.group( 1 ), UserData.SHARED );
								logger.debug( "  Found NO_MERGE for vertex: " + label );
							}



							// If BLOCKED is defined, find it...
							// If defined, it means that this vertex will not be added to the graph
							// Sometimes it can be useful during testing to mark vertcies as BLOCKED
							// due to bugs in the system you test. When the bug is removed, the BLOCKED
							// tag can be removed.
							p = Pattern.compile( "\\n(BLOCKED)", Pattern.MULTILINE );
							m = p.matcher( str );
							if ( m.find() )
							{
								logger.debug( "  Found BLOCKED. This vetex will be removed from the graph: " + label );
								v.addUserDatum( Keywords.BLOCKED, Keywords.BLOCKED, UserData.SHARED );
							}



							// If INDEX is defined, find it...
							// If defined, it means that this vertex has already a unique id gnerated
							// by mbt before, so we use this instead..
							p = Pattern.compile( "\\n(INDEX=(.*))", Pattern.MULTILINE );
							m = p.matcher( str );
							if ( m.find() )
							{
								String index_key = m.group( 2 );
								logger.debug( "  Found INDEX. This vertex will use the INDEX key: " + index_key );
								v.setUserDatum( Keywords.INDEX_KEY, new Integer( index_key ), UserData.SHARED );
							}
						}
					}
					
					// Using the yEd editor, an image can be used to depict the vertex. When merging multiple
					// graphs into one, the code below, stores the image location, which will be used when
					// writing that merged graphml file.
					Iterator iterImage = element.getDescendants( new org.jdom.filter.ElementFilter( "Image" ) );
					while ( iterImage.hasNext() && currentVertex != null)
					{
						Object o2 = iterImage.next();
						if ( o2 instanceof org.jdom.Element )
						{
							org.jdom.Element image = (org.jdom.Element)o2;				
							if ( image.getAttributeValue( "href" ) != null )
							{
								logger.debug( "  Image: '" + image.getAttributeValue( "href" ) + "'" );
								currentVertex.addUserDatum( Keywords.IMAGE_KEY, image.getAttributeValue( "href" ), UserData.SHARED );
							}
						}
					}
					Iterator iterGeometry = element.getDescendants( new org.jdom.filter.ElementFilter( "Geometry" ) );
					while ( iterGeometry.hasNext() && currentVertex != null)
					{
						Object o2 = iterGeometry.next();
						if ( o2 instanceof org.jdom.Element )
						{
							org.jdom.Element geometry = (org.jdom.Element)o2;
							logger.debug( "  width: '" + geometry.getAttributeValue( "width" ) + "'" );
							logger.debug( "  height: '" + geometry.getAttributeValue( "height" ) + "'" );
							currentVertex.addUserDatum( Keywords.WIDTH_KEY, geometry.getAttributeValue( "width" ), UserData.SHARED );							
							currentVertex.addUserDatum( Keywords.HEIGHT_KEY, geometry.getAttributeValue( "height" ), UserData.SHARED );
						}
					}
				}
			}

			Object[] vertices = graph.getVertices().toArray();

			// Parse all edges (arrows or transitions)
			Iterator iter_edge = doc.getDescendants( new org.jdom.filter.ElementFilter( "edge" ) );
			while ( iter_edge.hasNext() )
			{
				Object o = iter_edge.next();
				if ( o instanceof org.jdom.Element )
				{
					org.jdom.Element element = (org.jdom.Element)o;
					logger.debug( "  id: " + element.getAttributeValue( "id" ) );

					Iterator iter2 = element.getDescendants( new org.jdom.filter.ElementFilter( "EdgeLabel" ) );
					org.jdom.Element edgeLabel = null;
					if ( iter2.hasNext() )
					{
						Object o2 = iter2.next();
						if ( o2 instanceof org.jdom.Element )
						{
							edgeLabel = (org.jdom.Element)o2;
							logger.debug( "  Full name: '" + edgeLabel.getQualifiedName() + "'" );
							logger.debug( "  Name: '" + edgeLabel.getTextTrim() + "'" );
						}
					}
					logger.debug( "  source: " + element.getAttributeValue( "source" ) );
					logger.debug( "  target: " + element.getAttributeValue( "target" ) );

					DirectedSparseVertex source = null;
					DirectedSparseVertex dest = null;

					for ( int i = 0; i < vertices.length; i++ )
					{
						DirectedSparseVertex vertex = (DirectedSparseVertex)vertices[ i ];

						// Find source vertex
						if ( vertex.getUserDatum( Keywords.ID_KEY ).equals( element.getAttributeValue( "source" ) ) &&
							 vertex.getUserDatum( Keywords.FILE_KEY ).equals( fileName ) )
						{
							source = vertex;
						}
						if ( vertex.getUserDatum( Keywords.ID_KEY ).equals( element.getAttributeValue( "target" ) ) &&
							 vertex.getUserDatum( Keywords.FILE_KEY ).equals( fileName ) )
						{
							dest = vertex;
						}
					}
					if ( source == null )
					{
						String msg = "Could not find starting node for edge. Name: '" + element.getAttributeValue( "source" ) + "' In file '" + fileName + "'";
						logger.error( msg );
						throw new RuntimeException( msg );
					}
					if ( dest == null )
					{
						String msg = "Could not find end node for edge. Name: '" + element.getAttributeValue( "target" ) + "' In file '" + fileName + "'";
						logger.error( msg );
						throw new RuntimeException( msg );
					}


					DirectedSparseEdge e = new DirectedSparseEdge( source, dest );
					graph.addEdge( e );
					e.addUserDatum( Keywords.ID_KEY,    element.getAttributeValue( "id" ),         UserData.SHARED );
					e.addUserDatum( Keywords.FILE_KEY,  fileName, 						          UserData.SHARED );
					e.addUserDatum( Keywords.INDEX_KEY, new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );


					if ( edgeLabel != null )
					{
						// The label of an edge has the following format:
						//   Label Parameter [Guard] / Action1;Action2;ActionN;
						//   Keyword
						// Where the Label, Parameter. Guard, Actions and Keyword are optional.
						
						String str = edgeLabel.getText();
						e.addUserDatum( Keywords.FULL_LABEL_KEY, str, UserData.SHARED );
						Pattern p = Pattern.compile( "(.*)", Pattern.MULTILINE );
						Matcher m = p.matcher( str );
						String label = null;
						if ( m.find() )
						{
							label = m.group( 1 );
							
							// Look for a Guard
							Pattern firstLinePattern = Pattern.compile( "\\[(.*)\\]", Pattern.MULTILINE );
							Matcher firstLineMatcher = firstLinePattern.matcher( label );
							if ( firstLineMatcher.find() )
							{
								String guard = firstLineMatcher.group( 1 );
								e.addUserDatum( Keywords.GUARD_KEY, guard, UserData.SHARED );
								logger.debug( " Found guard = '" + guard + "' for edge id: " + edgeLabel.getQualifiedName() );
							}							

							// Look for Actions
							if(label.indexOf("/")>0)
							{
								String actionPart = label.substring(label.indexOf("/")+1);
								firstLinePattern = Pattern.compile( ".*$", Pattern.MULTILINE );
								firstLineMatcher = firstLinePattern.matcher( actionPart );
								if ( firstLineMatcher.find() )
								{
									String actions = firstLineMatcher.group( 0 ).trim();
									e.addUserDatum( Keywords.ACTIONS_KEY, actions, UserData.SHARED );
									logger.debug( " Found actions: '" + actions + "' for edge id: " + edgeLabel.getQualifiedName() );
								}
							}
							
							// Look for the Label and Parameter
							firstLinePattern = Pattern.compile( "^(\\w+)\\s?([^\\s^/^\\[]+)?", Pattern.MULTILINE );
							firstLineMatcher = firstLinePattern.matcher( label );
							if ( firstLineMatcher.find() )
							{
								String label_key = firstLineMatcher.group( 1 );
								if ( Keywords.isKeyWord( label_key ) )
								{
									throw new RuntimeException( "Edge has a label '" + label  + "', which is a reserved keyword, in file: '" + fileName + "'" );
								}
								e.addUserDatum( Keywords.LABEL_KEY, label_key, UserData.SHARED );
								logger.debug( " Found label = '" + label_key + "' for edge id: " + edgeLabel.getQualifiedName() );
								
								String parameter = firstLineMatcher.group( 2 );
								if ( parameter != null )
								{
									e.addUserDatum( Keywords.PARAMETER_KEY, parameter, UserData.SHARED );
									logger.debug( " Found parameter = '" + parameter + "' for edge id: " + edgeLabel.getQualifiedName() );
								}
							}							
						}
						else
						{
							throw new RuntimeException( "Label for edge must be defined in file '" + fileName + "'" );
						}



						// If weight is defined, find it...
						// weight must be associated with a value, which depicts the probability for the edge
						// to be executed.
						// A value of 0.05 is the same as 5% chance of going down this road.
						p = Pattern.compile( "\\n(weight=(.*))", Pattern.MULTILINE );
						m = p.matcher( str );
						if ( m.find() )
						{
							Float weight;
							String value = m.group( 2 );
							try
							{
								weight = Float.valueOf( value.trim() );
								logger.debug( "  Found weight= " + weight + " for edge: " + label );
							}
							catch ( NumberFormatException error )
							{
								throw new RuntimeException( "For label: " + label + ", weight is not a correct float value: " + error.toString() + " In file '" + fileName + "'" );
							}
							e.addUserDatum( Keywords.WEIGHT_KEY, weight, UserData.SHARED );
						}



						// If BLOCKED is defined, find it...
						// If defined, it means that this edge will not be added to the graph
						// Sometimes it can be useful during testing to mark edges as BLOCKED
						// due to bugs in the system you test. When the bug is removed, the BLOCKED
						// tag can be removed.
						p = Pattern.compile( "\\n(BLOCKED)", Pattern.MULTILINE );
						m = p.matcher( str );
						if ( m.find() )
						{
							logger.debug( "  Found BLOCKED. This edge will be removed from the graph: " + label );
							e.addUserDatum( Keywords.BLOCKED, Keywords.BLOCKED, UserData.SHARED );
						}



						// If BACKTRACK is defined, find it...
						// If defined, it means that executing this edge may not lead to the desired
						// vertex. So, if that happens, the model gives the user a chance to try an
						// other edge from the previous vertex (the source vertex of the edge defined
						// with BACKTRACK)
						p = Pattern.compile( "\\n(BACKTRACK)", Pattern.MULTILINE );
						m = p.matcher( str );
						if ( m.find() )
						{
							logger.debug( "  Found BACKTRACK for edge: " + label );
							e.addUserDatum( Keywords.BACKTRACK, Keywords.BACKTRACK, UserData.SHARED );
						}



						// If INDEX is defined, find it...
						// If defined, it means that this edge has already a unique id generated
						// by mbt before, so we use this instead..
						p = Pattern.compile( "\\n(INDEX=(.*))", Pattern.MULTILINE );
						m = p.matcher( str );
						if ( m.find() )
						{
							String index_key = m.group( 2 );
							logger.debug( "  Found INDEX. This edge will use the INDEX key: " + index_key );
							e.setUserDatum( Keywords.INDEX_KEY, new Integer( index_key ), UserData.SHARED );
						}

					}
					e.addUserDatum( Keywords.VISITED_KEY, new Integer( 0 ), UserData.SHARED );
					logger.debug( "  Added edge: '" + e.getUserDatum( Keywords.LABEL_KEY ) + "', with id: " + e.getUserDatum( Keywords.INDEX_KEY ) );
				}
			}
		}
		catch ( JDOMException e )
		{
			logger.error( e );
			throw new RuntimeException( "Could not parse file: '" + fileName + "'" );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			throw new RuntimeException( "Could not parse file: '" + fileName + "'" );
		}

		removeBlockedEntities( graph );

		return graph;
	}

	/**
	 *  Increment and return the unique index for a vertex or edge.
	 */
	private int getNewVertexAndEdgeIndex()
	{
		return ++vertexAndEdgeIndex;
	}

	/**
	 *  Removes any edges, and any vertices that contains the key word BLOCKED
	 */
	private void removeBlockedEntities( SparseGraph graph )
	{
		Object[] vertices = graph.getVertices().toArray();
		for ( int i = 0; i < vertices.length; i++ )
		{
			DirectedSparseVertex v = (DirectedSparseVertex)vertices[ i ];
			if ( v.containsUserDatumKey( Keywords.BLOCKED ) )
			{
				logger.debug( "Removing this vertex because it is BLOCKED: '" + v.getUserDatum( Keywords.LABEL_KEY ) + "'" );
				graph.removeVertex( v );
			}
		}
		Object[] edges = graph.getEdges().toArray();
		for ( int i = 0; i < edges.length; i++ )
		{
			DirectedSparseEdge e = (DirectedSparseEdge)edges[ i ];
			if ( e.containsUserDatumKey( Keywords.BLOCKED ) )
			{
				logger.debug( "Removing this edge because it is BLOCKED: '" + e.getUserDatum( Keywords.LABEL_KEY ) + "'" );
				graph.removeEdge( e );
			}
		}
	}

	/**
	 *  Merge all file graphs into one graph.
	 */
	private void mergeAllGraphs()
	{
		if(!isMerged())
		{
			findMotherAndSubgraphs();
			checkForDuplicateVerticesInSubgraphs();
			mergeSubgraphs();
			mergeVerticesMarked_MERGE();
			checkForVerticesWithZeroInEdges();
	
			logger.info( "Done merging" );		
			setMerged(true);
		}
	}

	private void setMerged(boolean merged) 
	{
		this.merged = merged;
	}

	private boolean isMerged() 
	{
		return this.merged;
	}

	/**
	 * Search for the mother graph, and all subgraphs
	 */
	private void findMotherAndSubgraphs()
	{	
		boolean foundMotherStartGraph = false;
		boolean foundSubStartGraph = false;
		graph = null;
		
		for ( Iterator iter = parsedGraphList.iterator(); iter.hasNext(); )
		{
			SparseGraph g = (SparseGraph) iter.next();
			foundSubStartGraph = false;
	
			logger.debug( "Analyzing graph: " + g.getUserDatum( Keywords.FILE_KEY ) );
	
			Object[] vertices = g.getVertices().toArray();
			for ( int i = 0; i < vertices.length; i++ )
			{
				DirectedSparseVertex v = (DirectedSparseVertex)vertices[ i ];
	
				// Find all vertices that are start nodes (START_NODE)
				if ( v.getUserDatum( Keywords.LABEL_KEY ).equals( Keywords.START_NODE ) )
				{
					Object[] edges = v.getOutEdges().toArray();
					if ( edges.length != 1 )
					{
						throw new RuntimeException( "A Start vertex can only have one out edge, look in file: " + g.getUserDatum( Keywords.FILE_KEY ) );
					}
					DirectedSparseEdge edge = (DirectedSparseEdge)edges[ 0 ];
					if ( edge.containsUserDatumKey( Keywords.LABEL_KEY ) )
					{
						if ( foundMotherStartGraph )
						{
							if ( graph.getUserDatum( Keywords.FILE_KEY ).equals( g.getUserDatum( Keywords.FILE_KEY ) ) )
							{
								throw new RuntimeException( "Only one Start vertex can exist in one file, see file '" +
					                    graph.getUserDatum( Keywords.FILE_KEY )+ "'" );
							}
							else
							{
								throw new RuntimeException( "Only one Start vertex can exist in one file, see files " +
					                    graph.getUserDatum( Keywords.FILE_KEY )+ ", and " + g.getUserDatum( Keywords.FILE_KEY ) );								
							}
						}
						if ( foundSubStartGraph == true )
						{
							throw new RuntimeException( "Only one Start vertex can exist in one file, see file '" +
				                    g.getUserDatum( Keywords.FILE_KEY )+ "'" );		
						}
	
						foundMotherStartGraph = true;
						graph = g;
						edge.getDest().addUserDatum( Keywords.MOTHER_GRAPH_START_VERTEX, Keywords.MOTHER_GRAPH_START_VERTEX, UserData.SHARED );
						logger.debug( "Found the mother graph in the file: " +  graph.getUserDatum( Keywords.FILE_KEY ) );
					}
					else
					{
						if ( foundSubStartGraph == true )
						{
							throw new RuntimeException( "Only one Start vertex can exist in one file, see file '" +
				                    g.getUserDatum( Keywords.FILE_KEY )+ "'" );		
						}
						
						// Verify that current subgraph is not already defined
						for ( Iterator iter_g = parsedGraphList.iterator(); iter_g.hasNext(); )
						{
							if ( iter.hashCode() == iter_g.hashCode() )
							{
								continue;
							}
							
							SparseGraph tmp_graph = (SparseGraph) iter_g.next();
							if ( tmp_graph.containsUserDatumKey( Keywords.LABEL_KEY ) )
							{
								String name = (String) tmp_graph.getUserDatum( Keywords.LABEL_KEY );
								if ( name.equals( (String)edge.getDest().getUserDatum( Keywords.LABEL_KEY ) ) )
								{
									throw new RuntimeException( "Found 2 subgraphs using the same name: '" + 
											                    edge.getDest().getUserDatum( Keywords.LABEL_KEY ) + 
											                    "', they are defined in files: '" + 
											                    g.getUserDatum( Keywords.FILE_KEY ) + "', and :'"+
											                    tmp_graph.getUserDatum( Keywords.FILE_KEY ) + "'" );
								}
							}
						}
						
						if ( foundMotherStartGraph == true )
						{
							if ( graph.getUserDatum( Keywords.FILE_KEY ).equals( g.getUserDatum( Keywords.FILE_KEY ) ) )
							{
								throw new RuntimeException( "Only one Start vertex can exist in one file, see file '" +
					                    graph.getUserDatum( Keywords.FILE_KEY )+ "'" );
							}
						}
	
						// Since the edge does not contain a label, this is a subgraph
						// Mark the destination node of the edge to a subgraph starting node
						foundSubStartGraph = true;
						edge.getDest().addUserDatum( Keywords.SUBGRAPH_START_VERTEX, Keywords.SUBGRAPH_START_VERTEX, UserData.SHARED );
						g.addUserDatum( Keywords.LABEL_KEY, edge.getDest().getUserDatum( Keywords.LABEL_KEY ), UserData.SHARED );
						logger.debug( "Found sub-graph: '" + g.getUserDatum( Keywords.LABEL_KEY ) + "', in file '" + g.getUserDatum( Keywords.FILE_KEY ) + "'" );
						logger.debug( "Added SUBGRAPH_START_VERTEX to vertex: " + edge.getDest().getUserDatum( Keywords.INDEX_KEY ) );
					}
				}
			}
		}
		
		if ( graph == null )
		{
			throw new RuntimeException( "Did not find a Start vertex with an out edge with a label." );		
		}
	}
	
	/**
	 * Look for duplicated vertices in each sub-graph. If a vertex is found, which represents
	 * the name of the sub-graph (the vertex which the Start vertex points to) is duplicated
	 * in the same sub-graph, this will lead to an infinite recursive loop.
	 */
	private void checkForDuplicateVerticesInSubgraphs()
	{
		for ( int i = 0; i < parsedGraphList.size(); i++ )
		{
			SparseGraph g = (SparseGraph)parsedGraphList.elementAt( i );

			// Exclude the mother graph
			if ( graph.hashCode() == g.hashCode() )
			{
				continue;
			}

			logger.debug( "Looking for infinit recursive loop in file: " + g.getUserDatum( Keywords.FILE_KEY ) );

			String subgraph_label = (String)g.getUserDatum( Keywords.LABEL_KEY );
			Object[] vertices = g.getVertices().toArray();
			for ( int j = 0; j < vertices.length; j++ )
			{
				DirectedSparseVertex v = (DirectedSparseVertex)vertices[ j ];
				String label = (String)v.getUserDatum( Keywords.LABEL_KEY );
				if ( label.equals( subgraph_label ) )
				{
					if ( v.containsUserDatumKey( Keywords.SUBGRAPH_START_VERTEX ) )
					{
						continue;
					}
					if ( v.containsUserDatumKey( Keywords.NO_MERGE ) )
					{
						continue;
					}
					
					logger.error( "Vertex: " + label + ", with id: " + v.getUserDatum( Keywords.INDEX_KEY ) + ", is a duplicate in a subgraph" );
					throw new RuntimeException( "Found a subgraph containing a duplicate vertex with name: '" + 
		                    					v.getUserDatum( Keywords.LABEL_KEY ) + 
		                    					"', in file: '" + 
		                    					g.getUserDatum( Keywords.FILE_KEY ) + "'" );
					
				}
			}
			logger.debug( "Nope! Did not find any infinit recursive loops." );
		}
	}
	private void mergeSubgraphs()
	{
		for ( int i = 0; i < parsedGraphList.size(); i++ )
		{
			SparseGraph g = (SparseGraph)parsedGraphList.elementAt( i );

			if ( graph.hashCode() == g.hashCode() )
			{
				continue;
			}
			logger.debug( "Analysing graph in file: " + g.getUserDatum( Keywords.FILE_KEY ) );

			Object[] vertices = graph.getVertices().toArray();
			for ( int j = 0; j < vertices.length; j++ )
			{
				DirectedSparseVertex v1 = (DirectedSparseVertex)vertices[ j ];
				logger.debug( "Investigating vertex(" + v1.getUserDatum( Keywords.INDEX_KEY ) + "): '" + v1.getUserDatum( Keywords.LABEL_KEY ) + "'" );

				if ( v1.getUserDatum( Keywords.LABEL_KEY ).equals( g.getUserDatum( Keywords.LABEL_KEY ) ) )
				{
					if ( v1.containsUserDatumKey( Keywords.MERGE ) )
					{
						logger.debug( "The vertex is marked MERGE, and will not be replaced by a subgraph.");
						continue;
					}
					if ( v1.containsUserDatumKey( Keywords.NO_MERGE ) )
					{
						logger.debug( "The vertex is marked NO_MERGE, and will not be replaced by a subgraph.");
						continue;
					}
					if ( v1.containsUserDatumKey( Keywords.MERGED_BY_MBT ) )
					{
						logger.debug( "The vertex is marked MERGED_BY_MBT, and will not be replaced by a subgraph.");
						continue;
					}

					logger.debug( "A subgraph'ed vertex: '" + v1.getUserDatum( Keywords.LABEL_KEY ) +
							       "' in graph: " + g.getUserDatum( Keywords.FILE_KEY )  +
							       ", equals a node in the graph in file: '" +
							       graph.getUserDatum( Keywords.FILE_KEY ) + "'" );

					appendGraph( graph, g );
					copySubGraphs( graph, g, v1 );

					vertices = graph.getVertices().toArray();
					i = -1;
					j = -1;
				}
			}
		}
	}
	
	/**
	 * Merge all vertices marked MERGE
	 */
	private void mergeVerticesMarked_MERGE()
	{
		Object[] list1 = graph.getVertices().toArray();
		for ( int i = 0; i < list1.length; i++ )
		{
			DirectedSparseVertex v1 = (DirectedSparseVertex)list1[ i ];

			if ( v1.containsUserDatumKey( Keywords.MERGE ) == false )
			{
				continue;
			}

			Object[] list2 = graph.getVertices().toArray();
			Vector mergedVertices = new Vector();
			for ( int j = 0; j < list2.length; j++ )
			{
				DirectedSparseVertex v2 = (DirectedSparseVertex)list2[ j ];

				if ( v1.getUserDatum( Keywords.LABEL_KEY ).equals( v2.getUserDatum( Keywords.LABEL_KEY ) ) == false )
				{
					continue;
				}
				if ( v2.containsUserDatumKey( Keywords.NO_MERGE ) )
				{
					continue;
				}
				if ( v1.getUserDatum( Keywords.INDEX_KEY ) == v2.getUserDatum( Keywords.INDEX_KEY ) )
				{
					continue;
				}
				if ( mergedVertices.contains( v1 ) )
				{
					continue;
				}

				logger.debug( "Merging vertex(" + v1.getUserDatum( Keywords.INDEX_KEY ) + "): '" + v1.getUserDatum( Keywords.LABEL_KEY ) +
						       "' with vertex (" + v2.getUserDatum( Keywords.INDEX_KEY ) + ")" );

				Object[] inEdges = v1.getInEdges().toArray();
				for (int x = 0; x < inEdges.length; x++)
				{
					DirectedSparseEdge edge = (DirectedSparseEdge)inEdges[ x ];
					DirectedSparseEdge new_edge = (DirectedSparseEdge)graph.addEdge( new DirectedSparseEdge( edge.getSource(), v2 ) );
					new_edge.importUserData( edge );
					new_edge.setUserDatum( Keywords.INDEX_KEY, new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );
				}
				Object[] outEdges = v1.getOutEdges().toArray();
				for (int x = 0; x < outEdges.length; x++)
				{
					DirectedSparseEdge edge = (DirectedSparseEdge)outEdges[ x ];
					DirectedSparseEdge new_edge = (DirectedSparseEdge)graph.addEdge( new DirectedSparseEdge( v2, edge.getDest() ) );
					new_edge.importUserData( edge );
					new_edge.setUserDatum( Keywords.INDEX_KEY, new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );
				}
				mergedVertices.add( v1 );
			}

			if ( mergedVertices.isEmpty() == false )
			{
				logger.debug( "Remvoing merged vertex(" + v1.getUserDatum( Keywords.INDEX_KEY ) + ")" );
				graph.removeVertex( v1 );
			}
		}		
	}

	/**
	 * Search for any vertices with no in edges
	 */
	private void checkForVerticesWithZeroInEdges() throws RuntimeException
	{
		Object[] vs = graph.getVertices().toArray();
		for ( int i = 0; i < vs.length; i++ )
		{
			DirectedSparseVertex v = (DirectedSparseVertex)vs[ i ];
			if ( !v.getUserDatum( Keywords.LABEL_KEY ).equals( Keywords.START_NODE ) )
			{
				if ( v.getInEdges().toArray().length == 0 )
				{
					String msg = "No in-edges! The vertex: " + Util.getCompleteVertexName( v ) + " is not reachable," +
							     " from file: '" + v.getUserDatum( Keywords.FILE_KEY ) + "'";
					logger.error( msg );
					throw new RuntimeException( msg );
				}
			}
		}
	}
	
	/**
	 * Copies the graph src, into the graph dst
	 * @param dst
	 * @param src
	 */
	private void appendGraph( SparseGraph dst, SparseGraph src )
	{
		HashMap map = new HashMap();
		Object[] vertices = src.getVertices().toArray();
		for ( int i = 0; i < vertices.length; i++ )
		{
			DirectedSparseVertex v = (DirectedSparseVertex)vertices[ i ];
			if ( v.getUserDatum( Keywords.LABEL_KEY ).equals( Keywords.START_NODE ) )
			{
				continue;
			}
			DirectedSparseVertex new_v = new DirectedSparseVertex();
			new_v.importUserData( v );
			new_v.setUserDatum( Keywords.INDEX_KEY, new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );
			dst.addVertex( new_v );
			logger.debug("Associated vertex: " + v + " to new vertex: " + new_v );
			map.put( (Integer)v.getUserDatum( Keywords.INDEX_KEY ), new_v );
		}
		Object[] edges = src.getEdges().toArray();
		for ( int i = 0; i < edges.length; i++ )
		{
			DirectedSparseEdge e = (DirectedSparseEdge)edges[ i ];
			DirectedSparseVertex v1 = (DirectedSparseVertex)map.get( (Integer)e.getSource().getUserDatum( Keywords.INDEX_KEY ) );
			DirectedSparseVertex v2 = (DirectedSparseVertex)map.get( (Integer)e.getDest().getUserDatum( Keywords.INDEX_KEY ) );
			if ( v1 == null || v2 == null )
			{
				continue;
			}
			DirectedSparseEdge new_e = new DirectedSparseEdge( v1, v2 );
			new_e.importUserData( e );
			new_e.setUserDatum( Keywords.INDEX_KEY, new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );
			dst.addEdge( new_e );
		}
	}
	
	/**
	 * Replaces the vertex targetVertex and all its in and out edges, in the graph g, with all
	 * other vertices with the same name.
	 * @param mainGraph
	 * @param subGraph
	 * @param targetVertex
	 */
	private void copySubGraphs( SparseGraph mainGraph, SparseGraph subGraph, DirectedSparseVertex targetVertex )
	{
		// Save the target vertex out-edge list
		Vector targetVertexOutEdgeList = new Vector();
		logger.debug( "Target vertex (" + Util.getCompleteVertexName( targetVertex ) + ") out-edge list" );
		for (Iterator iter = targetVertex.getOutEdges().iterator(); iter.hasNext();)
		{
			DirectedSparseEdge element = (DirectedSparseEdge) iter.next();
			logger.debug( "  " + Util.getCompleteEdgeName( element ) );
			targetVertexOutEdgeList.add( element );
		}

		DirectedSparseVertex sourceVertex = null;
		Object[] vertices = mainGraph.getVertices().toArray();
		for ( int i = 0; i < vertices.length; i++ )
		{
			DirectedSparseVertex v = (DirectedSparseVertex)vertices[ i ];
			if ( v.getUserDatum( Keywords.LABEL_KEY ).equals( targetVertex.getUserDatum( Keywords.LABEL_KEY ) ) )
			{
				if ( v.containsUserDatumKey( Keywords.SUBGRAPH_START_VERTEX ) == false )
				{
					continue;
				}
				if ( v.containsUserDatumKey( Keywords.MERGE ) )
				{
					continue;
				}
				if ( v.containsUserDatumKey( Keywords.NO_MERGE ) )
				{
					continue;
				}
				if ( v.containsUserDatumKey( Keywords.MERGED_BY_MBT ) )
				{
					continue;
				}
				if ( v.getUserDatum( Keywords.INDEX_KEY ) == targetVertex.getUserDatum( Keywords.INDEX_KEY ) )
				{
					continue;
				}

				sourceVertex = v;
				break;
			}
		}

		if ( sourceVertex == null )
		{
			return;
		}

		logger.debug( "Start merging target vertex: " + Util.getCompleteVertexName( targetVertex ) + " with source vertex: " + Util.getCompleteVertexName( sourceVertex ) );

		Object[] inEdges = sourceVertex.getInEdges().toArray();
		for (int i = 0; i < inEdges.length; i++)
		{
			DirectedSparseEdge edge = (DirectedSparseEdge)inEdges[ i ];
			DirectedSparseEdge new_edge = (DirectedSparseEdge)mainGraph.addEdge( new DirectedSparseEdge( edge.getSource(), targetVertex ) );
			new_edge.importUserData( edge );
			new_edge.setUserDatum( Keywords.INDEX_KEY, new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );
		}
		Object[] outEdges = sourceVertex.getOutEdges().toArray();
		for (int i = 0; i < outEdges.length; i++)
		{
			DirectedSparseEdge edge = (DirectedSparseEdge)outEdges[ i ];
			DirectedSparseEdge new_edge = (DirectedSparseEdge)mainGraph.addEdge( new DirectedSparseEdge( targetVertex, edge.getDest() ) );
			new_edge.importUserData( edge );
			new_edge.setUserDatum( Keywords.INDEX_KEY, new Integer( getNewVertexAndEdgeIndex() ), UserData.SHARED );
		}
		logger.debug( "Remvoing source vertex: " + Util.getCompleteVertexName( sourceVertex) );
		mainGraph.removeVertex( sourceVertex );
		targetVertex.addUserDatum( Keywords.MERGED_BY_MBT, Keywords.MERGED_BY_MBT, UserData.SHARED );

		
		// Check if there exists a Stop vertex.
		// Also check if there is only one.
		DirectedSparseVertex stopVertex = null;
		vertices = mainGraph.getVertices().toArray();
		for ( int i = 0; i < vertices.length; i++ )
		{
			DirectedSparseVertex v = (DirectedSparseVertex)vertices[ i ];
			if ( v.getUserDatum( Keywords.LABEL_KEY ).equals( Keywords.STOP_NODE ) )
			{
				if ( stopVertex != null )
				{
					throw new RuntimeException( "Found more than 1 Stop vertex in file (Only one Stop vertex per file is allowed): '" + 
							mainGraph.getUserDatum( Keywords.FILE_KEY ) + "'" );					
				}
				stopVertex = v;
			}
		}
		
		
		// All edges going to the Stop vertex, needs to be merged to the destination vertex.
		// The destination vertex, is pointed to by the vertex which is expanded by the sub graph.
		if ( stopVertex != null )
		{
			Vector edgesToBeRemoved = new Vector();
			inEdges = stopVertex.getInEdges().toArray();
			
			logger.debug( "Stop vertex in-edge list" );
			for (Iterator iter = stopVertex.getInEdges().iterator(); iter.hasNext();)
			{
				DirectedSparseEdge element = (DirectedSparseEdge) iter.next();
				logger.debug( "  " + Util.getCompleteEdgeName( element ) );
			}
			logger.debug( "Target vertex (" + Util.getCompleteVertexName( targetVertex ) + ") out-edge list" );
			for (Iterator iter = targetVertexOutEdgeList.iterator(); iter.hasNext();)
			{
				DirectedSparseEdge element = (DirectedSparseEdge) iter.next();
				logger.debug( "  " + Util.getCompleteEdgeName( element ) );
			}

			Vector mergeList = MergeList( targetVertexOutEdgeList.toArray(), inEdges );
			Object[] mergeListArray = mergeList.toArray();
			
			for ( int i = 0; i < mergeListArray.length; i++ )
			{
				Pair pair = (Pair)mergeListArray[ i ];
				MergeOutEdgeAndInEdge( (DirectedSparseEdge)pair.getFirst(), (DirectedSparseEdge)pair.getSecond(), edgesToBeRemoved, mainGraph );
			}									

			
			// Now remove the edges that has been copied.
			for (Iterator iter = edgesToBeRemoved.iterator(); iter.hasNext();)
			{
				DirectedSparseEdge element = (DirectedSparseEdge) iter.next();
				try {
					mainGraph.removeEdge( element );
					logger.debug( "Removing edge: " + Util.getCompleteEdgeName( element ) );
					logger.debug( Util.getCompleteEdgeName( element ) + ", was found and removed from graph,: '" + mainGraph.getUserDatum( Keywords.FILE_KEY ) + "'");
				} catch (java.lang.IllegalArgumentException e) {
					logger.debug( Util.getCompleteEdgeName( element ) + ", was not found in graph: '" + mainGraph.getUserDatum( Keywords.FILE_KEY ) + "', this is ok, since it probably been removed before. (I know, not ver good progamming practice here)");
				}
			}
			logger.debug( "Removing the Stop vertex: " + stopVertex.getUserDatum( Keywords.INDEX_KEY )  );
			mainGraph.removeVertex( stopVertex );
		}
	}
	
	private Vector MergeList( Object[] array_A, Object[] array_B )
	{
		logger.debug( "Vector twoLists( Object[] array_A, Object[] array_B )" );
		Vector matches = new Vector();
		logger.debug( "  Looking for exact matches" );
		for ( int i = 0; i < array_A.length; i++ )
		{
			DirectedSparseEdge a = (DirectedSparseEdge)array_A[ i ];			
			String aLabel = (String)a.getUserDatum( Keywords.LABEL_KEY );
			for ( int j = 0; j < array_B.length; j++ )
			{
				DirectedSparseEdge b = (DirectedSparseEdge)array_B[ j ];
				String bLabel = (String)b.getUserDatum( Keywords.LABEL_KEY );
				if ( aLabel != null && aLabel.length() == 0 )
				{
					aLabel = null;
				}
				if ( bLabel != null && bLabel.length() == 0 )
				{
					bLabel = null;
				}
				if ( aLabel == null && bLabel == null )
				{
					logger.debug( "    adding: " + Util.getCompleteEdgeName( a ) + " and " + Util.getCompleteEdgeName( b ) );
					matches.add( new Pair( a, b ) );
				}
				else if ( aLabel != null && bLabel != null )
				{
					if ( aLabel.equals( bLabel ) )
					{
						logger.debug( "    adding: " + Util.getCompleteEdgeName( a ) + " and " + Util.getCompleteEdgeName( b ) );
						matches.add( new Pair( a, b ) );
					}
				}
			}		
		}
		
		logger.debug( "  Matching nulls from the first list with non-matched items in the second list" );
		for ( int i = 0; i < array_A.length; i++ )
		{
			DirectedSparseEdge a = (DirectedSparseEdge)array_A[ i ];			
			String aLabel = (String)a.getUserDatum( Keywords.LABEL_KEY );
			if ( aLabel == null || aLabel.length() == 0 )
			{
				for ( int j = 0; j < array_B.length; j++ )
				{
					DirectedSparseEdge b = (DirectedSparseEdge)array_B[ j ];
					String bLabel = (String)b.getUserDatum( Keywords.LABEL_KEY );
					if ( bLabel != null )
					{
						boolean alreadyMatched = false;
						for (Iterator iter = matches.iterator(); iter.hasNext();)
						{
							Pair element = (Pair) iter.next();
							if ( b.equals( element.getSecond() ) )
							{
								alreadyMatched = true;
								break;
							}
						}
			
						if ( alreadyMatched == false )
						{
							logger.debug( "    adding: " + Util.getCompleteEdgeName( a ) + " and " + Util.getCompleteEdgeName( b ) );
							matches.add( new Pair( a, b ) );
						}
					}
				}
			}
		}
		
		logger.debug( "  Matching nulls from the second list with non-matched items in the first list" );
		for ( int i = 0; i < array_B.length; i++ )
		{
			DirectedSparseEdge b = (DirectedSparseEdge)array_B[ i ];			
			String bLabel = (String)b.getUserDatum( Keywords.LABEL_KEY );
			if ( bLabel == null || bLabel.length() == 0 )
			{
				for ( int j = 0; j < array_A.length; j++ )
				{
					DirectedSparseEdge a = (DirectedSparseEdge)array_A[ j ];
					String aLabel = (String)a.getUserDatum( Keywords.LABEL_KEY );
					if ( aLabel != null )
					{
						boolean alreadyMatched = false;
						for (Iterator iter = matches.iterator(); iter.hasNext();)
						{
							Pair element = (Pair) iter.next();
							if ( a.equals( element.getFirst() ) )
							{
								alreadyMatched = true;
								break;
							}
						}
			
						if ( alreadyMatched == false )
						{
							logger.debug( "    adding: " + Util.getCompleteEdgeName( a ) + " and " + Util.getCompleteEdgeName( b ) );
							matches.add( new Pair( a, b ) );
						}
					}
				}
			}
		}
		return matches;
	}
	
	
	private void merge_user_data( DirectedSparseEdge dst_edge, DirectedSparseEdge src_edge_A , DirectedSparseEdge src_edge_B )
	{
		logger.debug( "    merge_user_data" );
		for (Iterator iter = src_edge_A.getUserDatumKeyIterator(); iter.hasNext();)
		{
			String element = (String) iter.next();
			if ( dst_edge.containsUserDatumKey( element ) == false )
			{
				logger.debug( "      addUserDatum: " + element + " = " + src_edge_A.getUserDatum( element ) );
				dst_edge.addUserDatum( element, src_edge_A.getUserDatum( element ), UserData.SHARED );
			}
		}
		for (Iterator iter = src_edge_B.getUserDatumKeyIterator(); iter.hasNext();)
		{
			String element = (String) iter.next();
			if ( dst_edge.containsUserDatumKey( element ) == false )
			{
				logger.debug( "      addUserDatum: " + element + " = " + src_edge_B.getUserDatum( element ) );
				dst_edge.addUserDatum( element, src_edge_B.getUserDatum( element ), UserData.SHARED );
			}
		}
		String fullLabel_A = (String)src_edge_A.getUserDatum( Keywords.FULL_LABEL_KEY ); 
		String fullLabel_B = (String)src_edge_B.getUserDatum( Keywords.FULL_LABEL_KEY );
		
		if ( fullLabel_A != null && fullLabel_B != null )
		{
			if ( fullLabel_A.length() > fullLabel_B.length() )
			{
				logger.debug( "      full label: " +  src_edge_A.getUserDatum( Keywords.FULL_LABEL_KEY ) );
				dst_edge.setUserDatum( Keywords.FULL_LABEL_KEY, src_edge_A.getUserDatum( Keywords.FULL_LABEL_KEY ), UserData.SHARED );
			}
			else
			{
				logger.debug( "      full label: " +  src_edge_B.getUserDatum( Keywords.FULL_LABEL_KEY ) );
				dst_edge.setUserDatum( Keywords.FULL_LABEL_KEY, src_edge_B.getUserDatum( Keywords.FULL_LABEL_KEY ), UserData.SHARED );
			}
		}
		else if ( fullLabel_A != null  && fullLabel_B == null )
		{
			logger.debug( "      full label: " +  src_edge_A.getUserDatum( Keywords.FULL_LABEL_KEY ) );
			dst_edge.setUserDatum( Keywords.FULL_LABEL_KEY, src_edge_A.getUserDatum( Keywords.FULL_LABEL_KEY ), UserData.SHARED );
		}
		else if ( fullLabel_A == null  && fullLabel_B != null )
		{
			logger.debug( "      full label: " +  src_edge_B.getUserDatum( Keywords.FULL_LABEL_KEY ) );
			dst_edge.setUserDatum( Keywords.FULL_LABEL_KEY, src_edge_B.getUserDatum( Keywords.FULL_LABEL_KEY ), UserData.SHARED );
		}
	}
	
	private void MergeOutEdgeAndInEdge(DirectedSparseEdge outEdge,
			DirectedSparseEdge inEdge, Vector edgesToBeRemoved,
			SparseGraph graph) {
		logger.debug("MergeOutEdgeAndInEdge");

		if (outEdge == null) {
			throw new RuntimeException("Internal progamming error");
		}
		if (inEdge == null) {
			throw new RuntimeException("Internal progamming error");
		}

		logger.debug("  outEdge: " + Util.getCompleteEdgeName(outEdge));
		logger.debug("  inEdge: " + Util.getCompleteEdgeName(inEdge));

		DirectedSparseEdge new_edge = (DirectedSparseEdge) graph
				.addEdge(new DirectedSparseEdge((DirectedSparseVertex) inEdge
						.getSource(), outEdge.getDest()));

		merge_user_data(new_edge, outEdge, inEdge);

		new_edge.setUserDatum( Keywords.INDEX_KEY,
				new Integer(getNewVertexAndEdgeIndex()), UserData.SHARED);
		logger.debug("  Replacing the target vertex out-edge: "
				+ Util.getCompleteEdgeName(outEdge) + " (old) with: "
				+ Util.getCompleteEdgeName(new_edge) + "(new), using: "
				+ Util.getCompleteEdgeName(inEdge));

		edgesToBeRemoved.add(inEdge);
		edgesToBeRemoved.add(outEdge);
	}

	/**
	 * Writes the graph to a PrintStream, using GraphML format.
	 */
	public void save(PrintStream ps) {
		SparseGraph g = getModel();
		ps.println( "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" );
		ps.println( "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns/graphml\"  " +
				            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
				            "xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns/graphml " +
				            "http://www.yworks.com/xml/schema/graphml/1.0/ygraphml.xsd\" " +
				            "xmlns:y=\"http://www.yworks.com/xml/graphml\">" );
		ps.println( "  <key id=\"d0\" for=\"node\" yfiles.type=\"nodegraphics\"/>" );
		ps.println( "  <key id=\"d1\" for=\"edge\" yfiles.type=\"edgegraphics\"/>" );
		ps.println( "  <graph id=\"G\" edgedefault=\"directed\">" );

        int numVertices = g.getVertices().size();
        edu.uci.ics.jung.graph.decorators.Indexer id = edu.uci.ics.jung.graph.decorators.Indexer.getAndUpdateIndexer( graph );
        for ( int i = 0; i < numVertices; i++ )
        {
            Vertex v = (Vertex) id.getVertex(i);
            int vId = i+1;

			ps.println( "    <node id=\"n" + vId + "\">" );
			ps.println( "      <data key=\"d0\" >" );

			if ( v.containsUserDatumKey( Keywords.IMAGE_KEY ) )
			{
				ps.println( "        <y:ImageNode >" );
				ps.println( "          <y:Geometry  x=\"241.875\" y=\"158.701171875\" width=\"" +
						                        v.getUserDatum( Keywords.WIDTH_KEY ) + "\" height=\"" +
						                        v.getUserDatum( Keywords.HEIGHT_KEY ) + "\"/>" );
			}
			else
			{
				ps.println( "        <y:ShapeNode >" );
				ps.println( "          <y:Geometry  x=\"241.875\" y=\"158.701171875\" width=\"95.0\" height=\"30.0\"/>" );
			}
			
			ps.println( "          <y:Fill color=\"#CCCCFF\"  transparent=\"false\"/>" );
			ps.println( "          <y:BorderStyle type=\"line\" width=\"1.0\" color=\"#000000\" />" );
			ps.println( "          <y:NodeLabel x=\"1.5\" y=\"5.6494140625\" width=\"92.0\" height=\"18.701171875\" " +
					                         "visible=\"true\" alignment=\"center\" fontFamily=\"Dialog\" fontSize=\"12\" " +
					                         "fontStyle=\"plain\" textColor=\"#000000\" modelName=\"internal\" modelPosition=\"c\" " +
					                         "autoSizePolicy=\"content\">" + v.getUserDatum( Keywords.FULL_LABEL_KEY ) + 
					                         "&#xA;INDEX=" + v.getUserDatum( Keywords.INDEX_KEY ) + "</y:NodeLabel>" );
			
			if ( v.containsUserDatumKey( Keywords.IMAGE_KEY ) )
			{
				ps.println( "          <y:Image href=\"" + v.getUserDatum( Keywords.IMAGE_KEY ) + "\"/>" );
				ps.println( "        </y:ImageNode>" );
			}
			else
			{
				ps.println( "          <y:Shape type=\"rectangle\"/>" );
				ps.println( "        </y:ShapeNode>" );
			}
			
			ps.println( "      </data>" );
			ps.println( "    </node>" );
		}

        int i = 0;
        for ( Iterator edgeIterator = g.getEdges().iterator(); edgeIterator.hasNext(); )
        {
            Edge e = (Edge) edgeIterator.next();
            Pair p = e.getEndpoints();
            Vertex src = (Vertex) p.getFirst();
            Vertex dest = (Vertex) p.getSecond();
            int srcId = id.getIndex(src)+1;
            int destId = id.getIndex(dest)+1;
            int nId = ++i;

            ps.println( "    <edge id=\"" + nId + "\" source=\"n" + srcId + "\" target=\"n" + destId + "\">" );
            ps.println( "      <data key=\"d1\" >" );
            ps.println( "        <y:PolyLineEdge >" );
            ps.println( "          <y:Path sx=\"-23.75\" sy=\"15.0\" tx=\"-23.75\" ty=\"-15.0\">" );
            ps.println( "            <y:Point x=\"273.3125\" y=\"95.0\"/>" );
            ps.println( "            <y:Point x=\"209.5625\" y=\"95.0\"/>" );
            ps.println( "            <y:Point x=\"209.5625\" y=\"143.701171875\"/>" );
            ps.println( "            <y:Point x=\"265.625\" y=\"143.701171875\"/>" );
            ps.println( "          </y:Path>" );
            ps.println( "          <y:LineStyle type=\"line\" width=\"1.0\" color=\"#000000\" />" );
            ps.println( "          <y:Arrows source=\"none\" target=\"standard\"/>" );
            
            if ( e.containsUserDatumKey( Keywords.FULL_LABEL_KEY ) )
            {
            	String label = (String)e.getUserDatum( Keywords.FULL_LABEL_KEY );
            	label = label.replaceAll( "&", "&amp;" );
            	label = label.replaceAll( "<", "&lt;" );
            	label = label.replaceAll( ">", "&gt;" );
            	label = label.replaceAll( "'", "&apos;" );
            	label = label.replaceAll( "\"", "&quot;" );
            	
            	ps.println( "          <y:EdgeLabel x=\"-148.25\" y=\"30.000000000000014\" width=\"169.0\" height=\"18.701171875\" " +
            			                         "visible=\"true\" alignment=\"center\" fontFamily=\"Dialog\" fontSize=\"12\" " +
            			                         "fontStyle=\"plain\" textColor=\"#000000\" modelName=\"free\" modelPosition=\"anywhere\" " +
            			                         "preferredPlacement=\"on_edge\" distance=\"2.0\" ratio=\"0.5\">" + label + 
            			                         "&#xA;INDEX=" + e.getUserDatum( Keywords.INDEX_KEY ) + "</y:EdgeLabel>" );
            }
            
            ps.println( "          <y:BendStyle smoothed=\"false\"/>" );
            ps.println( "        </y:PolyLineEdge>" );
            ps.println( "      </data>" );
            ps.println( "    </edge>" );

        }

        ps.println( "  </graph>" );
        ps.println( "</graphml>" );

	}
}
