package com.facebook.presto.spi.block;

import com.facebook.presto.spi.block.BlockDecoder;
import com.facebook.presto.spi.block.IntArrayAllocator;
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;

// Utilities for dealing with nulls and other common aspects of expression
// over decoded Blocks.
public class ExprContext
{
    
    IntArrayAllocator intArrayAllocator = new IntArrayAllocator();
    boolean[] nullsInReserve;
    boolean[] nullsInBatch;

    
    static void boolArrayOr(boolean[] target, boolean[] source, int[] map, int positionCount) {
      if (map == null) {
	  int i = 0;
	  /*
	  int bytesInWords = positionCount & ~7;
	  for (; i < bytesInWords; i += 8) {
	      unsafe.putLong(target, 16, unsafe.getLong(target, 16 + i) |
			     unsafe.getLong(source, 16 + i));
	  }
	  */
	  for (; i < positionCount; ++i) {
          target[i] |= source[i];
        }
      } else {
        for (int i = 0; i < positionCount; ++i) {
          target[i] |= source[map[i]];
        }
      }
    }

    void addNullFlags(boolean[] nullFlags, int[] map, int positionCount)
    {
      if (nullFlags != null) {
        if (nullsInBatch == null && map == null) {
          nullsInBatch = nullFlags;
        } else {
          boolean[]  newNulls;
          if (nullsInReserve !=null && nullsInReserve.length >= positionCount) {
            newNulls = nullsInReserve;
          } else {
            newNulls = new boolean[positionCount];
            nullsInReserve = newNulls;
          }
          if (nullsInBatch != null) {
            System.arraycopy(nullsInBatch, 0, newNulls, 0, positionCount);

          }
            nullsInBatch = newNulls;
            boolArrayOr(nullsInBatch, nullFlags, map, positionCount);
            }
          }
    }



}
    