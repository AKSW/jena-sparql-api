package org.aksw.jena_sparql_api.cache.staging;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.aksw.commons.collections.IClosable;
import org.aksw.commons.collections.SinglePrefetchIterator;
import org.aksw.commons.util.strings.StringUtils;
import org.aksw.jena_sparql_api.cache.extra.CacheEntryImpl;
import org.aksw.jena_sparql_api.cache.extra.SqlUtils;

//interface InputStreamProviderClosableFactory {
//	InputStreamProvider createInputStream
//}

public class CacheCoreIterator
	extends SinglePrefetchIterator<CacheEntryImpl>
{
	private ResultSet rs;
	
	// The action to perform when closing the input stream of a generated
	// cache entry. E.g. close the result set, commit the transaction, ...
	private IClosable inputStreamCloseAction;
	
	public CacheCoreIterator(ResultSet rs, IClosable inputStreamCloseAction) {
		this.rs = rs;
		this.inputStreamCloseAction = inputStreamCloseAction;
	}
	
	@Override
	protected CacheEntryImpl prefetch()
			throws Exception
	{	
		if (rs.next()) {

			byte[] rawQueryHash = rs.getBytes("id");
			String queryHash = StringUtils.bytesToHexString(rawQueryHash);

			String queryString = rs.getString("query_string");
			//Blob data = rs.getBlob("data");
			//InputStream data = rs.getBinaryStream("data");
			String str = rs.getString("data");
			
			InputStream data = new ByteArrayInputStream(str.getBytes());
			
			Timestamp timeOfInsertion = rs.getTimestamp("time_of_insertion");
			Timestamp timeOfExpiration = rs.getTimestamp("time_of_expiration");


			CacheEntryImpl result = new CacheEntryImpl(
					timeOfInsertion.getTime(),
					24 * 60 * 60 * 1000l, //timeOfExpiration.g,
					//new InputStreamProviderBlobClosable(data, inputStreamCloseAction),
                    new InputStreamProviderInputStreamClosable(data, inputStreamCloseAction),
					queryString,
					queryHash
			);
			
			
			return result;
		}
		
		return finish();
	}
	
	@Override
	public void close() {
		SqlUtils.close(rs);
	}
}