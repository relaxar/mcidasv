/*
 * $Id$
 *
 * Copyright 2007-2008
 * Space Science and Engineering Center (SSEC)
 * University of Wisconsin - Madison,
 * 1225 W. Dayton Street, Madison, WI 53706, USA
 *
 * http://www.ssec.wisc.edu/mcidas
 *
 * This file is part of McIDAS-V.
 * 
 * McIDAS-V is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * McIDAS-V is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see http://www.gnu.org/licenses
 */

package edu.wisc.ssec.mcidasv.data.hydra;

import java.util.HashMap;

public class RangeProcessor {

  static RangeProcessor createRangeProcessor(MultiDimensionReader reader, HashMap metadata) throws Exception {
    if (metadata.get("scale_name") == null) {
      String product_name = (String) metadata.get(SwathAdapter.product_name);
      if (product_name == "IASI_L1C_xxx") {
        return new IASI_RangeProcessor();
      }
      return null;
    }
    else {
      return new RangeProcessor(reader, metadata);
    }
  }

  MultiDimensionReader reader;
  HashMap metadata;

  float[] scale = null;
  float[] offset = null;
  float[] missing = null;
  float[] low = new float[] {Float.MIN_VALUE};
  float[] high = new float[] {Float.MAX_VALUE};

  public RangeProcessor() {

  }

  public RangeProcessor(MultiDimensionReader reader, HashMap metadata) throws Exception {
    this.reader = reader;
    this.metadata = metadata;

    HDFArray scaleAttr = reader.getArrayAttribute((String)metadata.get("array_name"), (String)metadata.get("scale_name"));

    if (scaleAttr.getType().equals(Float.TYPE)) {
      float[] attr = (float[]) scaleAttr.getArray();
      scale = new float[attr.length];
      for (int k=0; k<attr.length; k++) scale[k] = attr[k];
    }
    else if (scaleAttr.getType().equals(Short.TYPE)) {
      short[] attr = (short[]) scaleAttr.getArray();
      scale = new float[attr.length];
      for (int k=0; k<attr.length; k++) scale[k] = (float) attr[k];
    }
    if (scaleAttr.getType().equals(Double.TYPE)) {
      double[] attr = (double[]) scaleAttr.getArray();
      scale = new float[attr.length];
      for (int k=0; k<attr.length; k++) scale[k] = (float) attr[k];
    }

    
    HDFArray offsetAttr = reader.getArrayAttribute((String)metadata.get("array_name"), (String)metadata.get("offset_name"));

    if (offsetAttr.getType().equals(Float.TYPE)) {
      float[] attr = (float[]) offsetAttr.getArray();
      offset = new float[attr.length];
      for (int k=0; k<attr.length; k++) offset[k] = attr[k];
    }
    else if (offsetAttr.getType().equals(Short.TYPE)) {
      short[] attr = (short[]) offsetAttr.getArray();
      offset = new float[attr.length];
      for (int k=0; k<attr.length; k++) offset[k] = (float) attr[k];
    }
    if (offsetAttr.getType().equals(Double.TYPE)) {
      double[] attr = (double[]) offsetAttr.getArray();
      offset = new float[attr.length];
      for (int k=0; k<attr.length; k++) offset[k] = (float) attr[k];
    }

    offsetAttr = reader.getArrayAttribute((String)metadata.get("array_name"), (String)metadata.get("fill_value_name"));
    if (offsetAttr.getType().equals(Float.TYPE)) {
      float[] attr = (float[]) offsetAttr.getArray();
      missing = new float[attr.length];
      for (int k=0; k<attr.length; k++) missing[k] = attr[k];
    }
    else if (offsetAttr.getType().equals(Short.TYPE)) {
      short[] attr = (short[]) offsetAttr.getArray();
      missing = new float[attr.length];
      for (int k=0; k<attr.length; k++) missing[k] = (float) attr[k];
    }
    if (offsetAttr.getType().equals(Double.TYPE)) {
      double[] attr = (double[]) offsetAttr.getArray();
      missing = new float[attr.length];
      for (int k=0; k<attr.length; k++) missing[k] = (float) attr[k];
    }

  }

  public float[] processRange(short[] values, HashMap subset) {
     int channelIndex  = (int) ((double[])subset.get(SpectrumAdapter.channelIndex_name))[0];
     float[] new_values = new float[values.length];
     for (int k=0; k<values.length;k++) {
       float val = (float) values[k];
       if ((val == missing[0]) || (val < low[0]) || (val > high[0])) {
         new_values[k] = Float.NaN;
       }
       else {
         new_values[k] = scale[channelIndex]*(val - offset[channelIndex]);
       }
     }
     return new_values;
  }

  public float[] processRange(short[] values) {
     float[] new_values = new float[values.length];
     for (int k=0; k<values.length;k++) {
       float val = (float) values[k];
       if ((val == missing[0]) || (val < low[0]) || (val > high[0])) {
         new_values[k] = Float.NaN;
       }
       else {
         new_values[k] = scale[k]*(val - offset[k]);
       }
     }
     return new_values;
  }


}

class IASI_RangeProcessor extends RangeProcessor {

  public IASI_RangeProcessor() throws Exception {
    super();
  }

  public float[] processRange(short[] values, HashMap subset) {
    int channelIndex = (int) ((double[]) subset.get(SpectrumAdapter.channelIndex_name))[0];

    float[] new_values = IASI_L1C_Utility.getDecodedIASIImage(values, null, channelIndex);

    double[] track_coords = (double[]) subset.get(SwathAdapter.track_name);
    double[] xtrack_coords = (double[]) subset.get(SwathAdapter.xtrack_name);

    int numElems = ((int)(xtrack_coords[1] - xtrack_coords[0]) + 1);
    int numLines = ((int)(track_coords[1] - track_coords[0]) + 1);

    new_values = IASI_L1C_Utility.psuedoScanReorder2(new_values, 60, numLines*2); 

    //- subset here, if necessary

    return new_values;
  }

}
