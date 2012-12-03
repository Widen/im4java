/**************************************************************************
/* This class implements an image-information object.
/*
/* Copyright (c) 2009 by Bernhard Bablok (mail@bablokb.de)
/*
/* This program is free software; you can redistribute it and/or modify
/* it under the terms of the GNU Library General Public License as published
/* by  the Free Software Foundation; either version 2 of the License or
/* (at your option) any later version.
/*
/* This program is distributed in the hope that it will be useful, but
/* WITHOUT ANY WARRANTY; without even the implied warranty of
/* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
/* GNU Library General Public License for more details.
/*
/* You should have received a copy of the GNU Library General Public License
/* along with this program; see the file COPYING.LIB.  If not, write to
/* the Free Software Foundation Inc., 59 Temple Place - Suite 330,
/* Boston, MA  02111-1307 USA
/**************************************************************************/

package org.im4java.core;

import java.util.*;
import java.io.*;

import org.im4java.process.ArrayListOutputConsumer;
import org.im4java.process.Pipe;

/**
   This class implements an image-information object. The one-argument
   constructor expects a filename and parses the output of the
   "identify -verbose" command to create a hashtable of properties. This
   is the so called complete information. The two-argument constructor
   has a boolean flag as second argument. If you pass true, the Info-object
   only creates a set of so called basic information. This is more
   efficient since only a subset of the attributes of the image are
   requested and parsed.

   <p>
   Since the output of "identify -verbose" is meant as an human-readable
   interface parsing it is inherently flawed. This implementation
   interprets every line with a colon as a key-value-pair. This is not
   necessarely correct, e.g. the comment-field could be multi-line with
   colons within the comment.
   </p>

   <p>
   An alternative to the Info-class is to use the exiftool-command and
   the wrapper for it provided by im4java.
   </p>

   @version $Revision: 1.14 $
   @author  $Author: bablokb $

   @since 0.95
*/

public class  Info {

  //////////////////////////////////////////////////////////////////////////////

  /**
     Internal hashtable with image-attributes. For images with multiple
     scenes, this hashtable holds the attributes of the last scene.
  */

  private Hashtable<String,String> iAttributes = null;

  //////////////////////////////////////////////////////////////////////////////

  /**
     List of hashtables with image-attributes. Ther is one element for every
     scene in the image.

     @since 1.3.0
  */

  private LinkedList<Hashtable<String,String>> iAttribList =
                                     new LinkedList<Hashtable<String,String>>();

  //////////////////////////////////////////////////////////////////////////////

  /**
     Current value of indentation level
  */

  private int iOldIndent=0;

  //////////////////////////////////////////////////////////////////////////////

  /**
     Current value of attribute-prefix
  */

  private String iPrefix="";

  //////////////////////////////////////////////////////////////////////////////

    /**
     * Identity command path
     */
  private String commandPath;

  /**
     This contstructor will automatically parse the full output
     of identify -verbose.

     @param pImage  Source image
     @since 0.95
  */

  public Info(String pImage) throws InfoException {
    this(pImage, false);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     This constructor creates an Info-object with basic or complete
     image-information (depending on the second argument).

     @param pImage  Source image
     @param basic   Set to true for basic information, to false for complete info
     @since 1.2.0
  */

  public Info(String pImage, boolean basic) throws InfoException {
    this(pImage, basic, null);
  }

    /**
     * This contstructor will automatically parse the full output
     * of identify -verbose.
     *
     * @param is Source image inputStream
     * @throws InfoException
     * @throws IOException
     */
    public Info(InputStream is) throws InfoException, IOException {
        this(is, false);
    }

    /**
     * This constructor creates an Info-object with basic or complete
     * image-information (depending on the second argument).
     *
     * @param is Source image inputStream
     * @param basic Set to true for basic information, to false for complete info
     * @throws InfoException
     * @throws IOException
     */
    public Info(InputStream is, boolean basic) throws InfoException, IOException {
        this(is, basic, null);
    }

    /**
     * This constructor creates an Info-object with basic or complete
     * image-information (depending on the second argument).
     * 
     * @param pImage
     *            Source image
     * @param basic
     *            Set to true for basic information, to false for complete info
     * @param commandPath
     * @throws InfoException
     */
    public Info(String pImage, boolean basic, String commandPath) throws InfoException {
        this.commandPath = commandPath;
        if (!basic) {
            getCompleteInfo(pImage);
        } else {
            getBaseInfo(pImage);
        }
    }

    /**
     * This constructor creates an Info-object with basic or complete
     * image-information (depending on the second argument).
     *
     * @param is Source image inputStream
     * @param basic Set to true for basic information, to false for complete info
     * @param commandPath Identity command path
     * @throws InfoException
     * @throws IOException
     */
    public Info(InputStream is, boolean basic, String commandPath) throws InfoException, IOException {
        this.commandPath = commandPath;
        if (!basic) {
            getCompleteInfo(is);
        } else {
            getBaseInfo(is);
        }
    }

    /**
     * new an IdentifyCmd Object with a commandPath
     * 
     * @return
     */
    private IdentifyCmd getIdentityCmd() {
        IdentifyCmd identify = new IdentifyCmd();
        if (commandPath != null) {
            identify.setSearchPath(commandPath);
        }
        return identify;
    }


    /**
     * Query complete image-information.
     *
     * @param is source image inputStream
     * @throws InfoException
     */
    private void getCompleteInfo(InputStream is) throws InfoException, IOException {
        IMOperation op = new IMOperation();
        op.verbose();
        op.addImage("-");
        Pipe pipe = new Pipe(is, null);
        try {
            IdentifyCmd identify = getIdentityCmd();
            identify.setInputProvider(pipe);
            ArrayListOutputConsumer output = new ArrayListOutputConsumer();
            identify.setOutputConsumer(output);
            identify.run(op);
            resolveCompleteInfo(output.getOutput());
        } catch (Exception ex) {
            throw new InfoException(ex);
        } finally {
            is.close();
        }
    }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Query complete image-information.

     @param pImage  Source image
     @since 1.2.0
  */

  private void getCompleteInfo(String pImage) throws InfoException {
    IMOperation op = new IMOperation();
    op.verbose();
    op.addImage(pImage);

    try {
      IdentifyCmd identify = getIdentityCmd();
      ArrayListOutputConsumer output = new ArrayListOutputConsumer();
      identify.setOutputConsumer(output);
      identify.run(op);
      resolveCompleteInfo(output.getOutput());
    } catch (Exception ex) {
      throw new InfoException(ex);
    }
  }

    /**
     * @param cmdOutput
     */
  private void resolveCompleteInfo(ArrayList<String> cmdOutput) {
      StringBuilder lineAccu = new StringBuilder(80);
      for (String line:cmdOutput) {
          if (line.length() == 0) {
              // accumulate empty line as part of current attribute
              lineAccu.append("\n\n");
          } else if (line.indexOf(':') == -1) {
              // interpret this as a continuation-line of the current attribute
              lineAccu.append("\n").append(line);
          } else if (lineAccu.length() > 0) {
              // new attribute, process old attribute first
              parseLine(lineAccu.toString());
              lineAccu = new StringBuilder(80);
              lineAccu.append(line);
          } else {
              // new attribute, but nothing old to process
              lineAccu.append(line);
          }
      }
      // process last item
      if (lineAccu.length() > 0) {
          parseLine(lineAccu.toString());
      }

      // finish and add last hashtable to linked-list
      addBaseInfo();
      iAttribList.add(iAttributes);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Add basic info to complete-info.

     @since 1.3.0
  */

  private void addBaseInfo() {
    // complete output does not include width, height, depth
    String[] dim;
    String geo = iAttributes.get("Geometry");
    if (geo != null) {
      dim = geo.split("x|\\+");
      iAttributes.put("Width",dim[0]);
      iAttributes.put("Height",dim[1]);
    }
    geo = iAttributes.get("Page geometry");
    if (geo != null) {
      dim = geo.split("x|\\+");
      iAttributes.put("PageWidth",dim[0]);
      iAttributes.put("PageHeight",dim[1]);
      iAttributes.put("PageGeometry",geo);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Parse line of identify-output
  */

  private void parseLine(String pLine) {
    // structure:
    //    indent attribute: value

    if (pLine.startsWith("Image:")) {
      // start of a new scene
      if (iAttributes != null) {
	addBaseInfo();
	iAttribList.add(iAttributes);
      }
      iAttributes = new Hashtable<String,String>();
    }
    int indent = pLine.indexOf(pLine.trim())/2;

    String[] parts = pLine.trim().split(": ",2);

    // check indentation level and remove prefix if necessary
    if (indent < iOldIndent) {
      // remove tokens from iPrefix
      int colonIndex=iPrefix.length()-1;
      for (int i=0;i<iOldIndent-indent;++i) {
	colonIndex = iPrefix.lastIndexOf(':',colonIndex-1);
      }
      if (colonIndex == -1) {
	iPrefix="";
      } else {
	iPrefix=iPrefix.substring(0,colonIndex+1);
      }
    }
    iOldIndent = indent;

    // add a new attribute or increase prefix
    if (parts.length == 1) {
      // no value => add attribute to attribute-prefix
      iPrefix=iPrefix+parts[0];
    } else {
      // value => add (key,value) to attributes
      iAttributes.put(iPrefix+parts[0],parts[1]);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Query basic image-information.

     @param pImage  Source image
     @since 1.2.0
  */

  private void getBaseInfo(String pImage) throws InfoException {
    // create operation
    IMOperation op = new IMOperation();
    op.ping();
    op.format("%m\n%w\n%h\n%g\n%W\n%H\n%G\n%z\n%r");
    op.addImage(pImage);

    try {
      // execute ...
      IdentifyCmd identify = getIdentityCmd();
      ArrayListOutputConsumer output = new ArrayListOutputConsumer();
      identify.setOutputConsumer(output);
      identify.run(op);

      // ... and parse result
      resolveBaseInfo(output.getOutput());
    } catch (Exception ex) {
      throw new InfoException(ex);
    }
  }

    //////////////////////////////////////////////////////////////////////////////

    /**
     Query basic image-information.

     @param is  Source image inputStream
     */
    private void getBaseInfo(InputStream is) throws InfoException, IOException {
        IMOperation op = new IMOperation();
        op.ping();
        op.format("%m\n%w\n%h\n%g\n%W\n%H\n%G\n%z\n%r");
        op.addImage("-");
        Pipe pipe = new Pipe(is, null);
        try {
            // execute ...
            IdentifyCmd identify = getIdentityCmd();
            identify.setInputProvider(pipe);
            ArrayListOutputConsumer output = new ArrayListOutputConsumer();
            identify.setOutputConsumer(output);
            identify.run(op);
            // ... and parse result
            resolveBaseInfo(output.getOutput());
        } catch (Exception ex) {
            throw new InfoException(ex);
        } finally {
            is.close();
        }
    }

    /**
     * @param cmdOutput
     */
    private void resolveBaseInfo(ArrayList<String> cmdOutput) {
        Iterator<String> iter = cmdOutput.iterator();

        iAttributes = new Hashtable<String,String>();
        iAttributes.put("Format",iter.next());
        iAttributes.put("Width",iter.next());
        iAttributes.put("Height",iter.next());
        iAttributes.put("Geometry",iter.next());
        iAttributes.put("PageWidth",iter.next());
        iAttributes.put("PageHeight",iter.next());
        iAttributes.put("PageGeometry",iter.next());
        iAttributes.put("Depth",iter.next());
        iAttributes.put("Class",iter.next());
        iAttribList.add(iAttributes);
    }


  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image format.
  */

  public String getImageFormat() {
    return iAttributes.get("Format");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image format for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public String getImageFormat(int pSceneNr) {
    return iAttribList.get(pSceneNr).get("Format");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image width.
  */

  public int getImageWidth() throws InfoException {
    return getImageWidth(iAttribList.size()-1);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image width for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public int getImageWidth(int pSceneNr) throws InfoException {
    try {
      return Integer.parseInt(iAttribList.get(pSceneNr).get("Width"));
    } catch (NumberFormatException ex) {
      throw new InfoException(ex);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image height.
  */

  public int getImageHeight() throws InfoException {
    return getImageHeight(iAttribList.size()-1);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image height for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public int getImageHeight(int pSceneNr) throws InfoException {
    try {
      return Integer.parseInt(iAttribList.get(pSceneNr).get("Height"));
    } catch (NumberFormatException ex) {
      throw new InfoException(ex);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image geometry.
  */

  public String getImageGeometry() {
    return iAttributes.get("Geometry");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image geometry for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public String getImageGeometry(int pSceneNr) {
    return iAttribList.get(pSceneNr).get("Geometry");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image depth. Note that this method just returns an integer
     (e.g. 8 or 16), and not a string ("8-bit") like getProperty("Depth")
     does.

     @since 1.3.0
  */

  public int getImageDepth() throws InfoException {
    return getImageDepth(iAttribList.size()-1);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image depth. Note that this method just returns an integer
     (e.g. 8 or 16), and not a string ("8-bit") like getProperty("Depth")
     does.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public int getImageDepth(int pSceneNr) throws InfoException {
    String[] depth = iAttribList.get(pSceneNr).get("Depth").split("-|/",2);
    try {
      return Integer.parseInt(depth[0]);
    } catch (NumberFormatException ex) {
      throw new InfoException(ex);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image class.
  */

  public String getImageClass() {
    return iAttributes.get("Class");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the image class for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public String getImageClass(int pSceneNr) {
    return iAttribList.get(pSceneNr).get("Class");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the page width.

     @since 1.3.0
  */

  public int getPageWidth() throws InfoException {
    return getPageWidth(iAttribList.size()-1);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the page width for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public int getPageWidth(int pSceneNr) throws InfoException {
    try {
      return Integer.parseInt(iAttribList.get(pSceneNr).get("PageWidth"));
    } catch (NumberFormatException ex) {
      throw new InfoException(ex);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the page height.

     @since 1.3.0
  */

  public int getPageHeight() throws InfoException {
    return getPageHeight(iAttribList.size()-1);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the page height for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public int getPageHeight(int pSceneNr) throws InfoException {
    try {
      return Integer.parseInt(iAttribList.get(pSceneNr).get("PageHeight"));
    } catch (NumberFormatException ex) {
      throw new InfoException(ex);
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the page geometry.

     @since 1.3.0
  */

  public String getPageGeometry() {
    return iAttributes.get("PageGeometry");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the page geometry for the given scene.

     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public String getPageGeometry(int pSceneNr) {
    return iAttribList.get(pSceneNr).get("PageGeometry");
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the given property.
  */

  public String getProperty(String pPropertyName) {
    return iAttributes.get(pPropertyName);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the given property of the given scene.

     @param pPropertyName Name of the property
     @param pSceneNr Scene-number (zero-based)

     @since 1.3.0
  */

  public String getProperty(String pPropertyName, int pSceneNr) {
    return iAttribList.get(pSceneNr).get(pPropertyName);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the number of scenes.

     @since 1.3.0
  */

  public int getSceneCount() {
    return iAttribList.size();
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return an enumeration of all properties.
  */

  public Enumeration<String> getPropertyNames() {
    return iAttributes.keys();
  }
}
