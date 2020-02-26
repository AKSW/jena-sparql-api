package org.aksw.jena_sparql_api.io.binseach;

import java.nio.ByteBuffer;


/**
 * A wrapper that virtually puts a displaced page view over a delegate
 * 
 * There must be a 1:1 correspondence between page and byte buffer.
 * Hence, if a virtual page stretches over multiple physical ones, the data is copied
 * into a buffer of sufficient size.
 * 
 * 
 * view: displacement [  ]  [  ]  [  ]  [  ]  [  ]  [  ]
 * delegate:    [  p1   ] [  p2   ] [  p3   ] [  p4   ] 
 * 
 * @author raven
 *
 */
public class PageManagerWrapper
	implements PageManager
{
	protected PageManager delegate;
	protected long displacement;
	protected int virtPageSize;

	public PageManagerWrapper(PageManager delegate, long displacement, int pageSize) {
		super();
		this.delegate = delegate;
		this.displacement = displacement;
		this.virtPageSize = pageSize;		
	}

	@Override
	public ByteBuffer requestBufferForPage(long page) {
		int physPageSize = delegate.getPageSize();

		//page *  pageSize;
		long effPos = page * virtPageSize - displacement;		
		long effPage = effPos / physPageSize;
		int effIndex = (int)effPos % physPageSize;

		long effEndPos = effPos + virtPageSize;
		long effEndPage = effEndPos / physPageSize;
		int effEndIndex = (int)effEndPos % physPageSize;
		
		ByteBuffer result;
		if(effPage == effEndPage) {
			ByteBuffer buf = delegate.requestBufferForPage(effPage);
			if(buf == null) {
				result = null;
			} else {
				int o = buf.position();
			//if(buf.remaining() > virtPageSize) {
				// We expect the page to have sufficient size
				result = buf.duplicate();
				
//				int start = o + effIndex;
//				if(start < 0) {
//					// create a new buffer and pad
//					
//				}
				
				result.position(o + effIndex);
				result.limit(o + effEndIndex);
			}
			//}			
		} else {
			byte[] cpy = new byte[virtPageSize];
			result = ByteBuffer.wrap(cpy);

			for(long i = effPage;; ++i) {
				ByteBuffer buf = delegate.requestBufferForPage(i);
				if(buf != null) {
					int o = buf.position();
					
					buf = buf.duplicate();
					int index = i == effPage ? effIndex : 0;
					buf.position(o + index);
					
					//int x = buf.remaining();
					int take = Math.min(buf.remaining(), result.remaining());
					buf.limit(buf.position() + take);
					result.put(buf);
					
					if(result.remaining() == 0) {
						result.position(0);
						break;
					}
				} else {
					break;
				}
			}
			
			
		}

		return result;
	}

	@Override
	public int getPageSize() {
		return virtPageSize;
	}
	@Override
	public long getEndPos() {
		long result = delegate.getEndPos();
		return result;
	}
}
