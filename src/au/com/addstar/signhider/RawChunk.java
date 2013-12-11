package au.com.addstar.signhider;

import org.apache.commons.lang.Validate;

public class RawChunk
{
	private byte[] mRaw;
	private int mExistsMap;

	public RawChunk(byte[] raw, int existsMap, int extMap)
	{
		mRaw = raw;
		mExistsMap = existsMap;
		
	}
	
	public int getSectionCount()
	{
		return Integer.bitCount(mExistsMap);
	}
	
	private boolean doesSegmentExist(int segment)
	{
		return (mExistsMap & (1 << segment)) != 0;
	}
	private int getActualSegment(int segment)
	{
		int index = 0;
		for(int i = 0; i < segment; ++i)
		{
			if(doesSegmentExist(segment))
				++index;
		}
		return index;
	}
	
	public int getBlockId(int x, int y, int z)
	{
		if(!doesSegmentExist(y >> 4))
			return 0;
		
		int seg = getActualSegment(y >> 4);
		
		int id = mRaw[seg*4096 + (y&0xF)*256 + z * 16 + x];
		
		// TODO: Should probably do ext block ids
		
		return id;
	}
	
	public void setBlockId(int x, int y, int z, int id)
	{
		Validate.isTrue(x >= 0 && x < 16);
		Validate.isTrue(z >= 0 && z < 16);
		
		if(!doesSegmentExist(y >> 4))
			return;
		
		int seg = getActualSegment(y >> 4);
		
		mRaw[seg*4096 + (y&0xF)*256 + z * 16 + x] = (byte)id;
	}
	
	
}
