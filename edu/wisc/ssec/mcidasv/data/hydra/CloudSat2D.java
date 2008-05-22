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

import visad.Data;
import visad.FlatField;
import visad.Set;
import visad.CoordinateSystem;
import visad.RealType;
import visad.RealTupleType;
import visad.SetType;
import visad.Linear2DSet;
import visad.Unit;
import visad.FunctionType;
import visad.VisADException;
import java.rmi.RemoteException;

import java.util.Hashtable;
import java.util.HashMap;
import java.util.StringTokenizer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class CloudSat2D extends ProfileAlongTrack {

      public CloudSat2D() {
      }

      public CloudSat2D(MultiDimensionReader reader, HashMap metadata) {
        super(reader, metadata);
      }

      public float[] getVertBinAltitude() throws Exception {
        String propertyFileName = null;
        float[] altitude = new float[VertLen];
        try {
        propertyFileName = (String) metadata.get(ancillary_file_name);
        InputStream ios = new FileInputStream(propertyFileName);
        BufferedReader ancillaryReader = new BufferedReader(new InputStreamReader(ios));
                                                                                                                                                     
        int line_cnt = 0;
                                                                                                                                                     
        while (true) {
          String line = ancillaryReader.readLine();
          if (line == null) break;
          if (line.startsWith("!")) continue;
          StringTokenizer strTok = new StringTokenizer(line);
          String[] tokens = new String[strTok.countTokens()];
          int tokCnt = 0;
          while (strTok.hasMoreElements()) {
            tokens[tokCnt++] = strTok.nextToken();
          }
          altitude[line_cnt] = Float.valueOf(tokens[0]);
          line_cnt++;
        }
        ios.close();
        }
        catch (Exception e) {
          System.out.println("fail on ancillary file read: "+propertyFileName);
        }
        return altitude;
      }

      public float[] getTrackTimes() throws Exception {
        return null;
      }
      public RealType makeVertLocType() throws Exception {
        return null;
      }
      public RealType makeTrackTimeType() throws Exception {
        return null;
      }
      public float[] getTrackLongitude() throws Exception {
        return null;
      }
      public float[] getTrackLatitude() throws Exception {
        return null;
      }

}
