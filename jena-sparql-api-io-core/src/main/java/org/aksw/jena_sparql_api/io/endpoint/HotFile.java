package org.aksw.jena_sparql_api.io.endpoint;

import java.io.IOException;
import java.io.InputStream;

/**
 * A hot file is a file currently being written to by another thread or process.
 * The HotFile API allows for waiting for the final file to become ready, or to
 * open input streams for concurrent reads
 * 
 * @author raven
 *
 */
public interface HotFile
	extends FileCreation
{
	/**
	 * Get the file being written to.
	 * The temp file may be moved to its final place using a
	 * (atomic) move operation to prevent potential conflicts
	 * - such as someone repeatedly triggering a workflow by clicking a button twice
	 * 
	 * @return
	 */
	// TODO Probably there is no use in exposing this here
	// Path getTemporaryFile();
	
	
	/**
	 * Open a new stream to the hot file
	 * In the worst case, the input stream delivery may be delayed
	 * until after the file creation is complete. 
	 * 
	 * TODO Add a flag to hint the methods behavior
	 * 
	 * @return
	 */
	InputStream newInputStream() throws IOException;
}