/**************************************************************************
/* An abstract implementation of a ScriptGenerator.
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

package org.im4java.script;

import java.util.*;
import java.io.*;
import org.im4java.core.*;

/**
   This class is an abstract implementation of a ScriptGenerator.

   @version $Revision: 1.7 $
   @author  $Author: bablokb $
 
   @since 1.0.0
 */

abstract public class AbstractScriptGenerator implements ScriptGenerator {

  //////////////////////////////////////////////////////////////////////////////

  /**
     The maximum linesize.
  */

  protected static final int LINE_SIZE=78;

  //////////////////////////////////////////////////////////////////////////////

  /**
     The EOL escape character.
  */

  protected char ESC_EOL;

  //////////////////////////////////////////////////////////////////////////////

  /**
     The escape character for special characters.
  */

  protected char ESC_SPECIAL;

  //////////////////////////////////////////////////////////////////////////////
  
  /**
     The current indentation-level.
  */

  protected String iIndent="";

  //////////////////////////////////////////////////////////////////////////////

  /**
     The current line-buffer.
  */

  protected StringBuilder iLineBuffer = null;

  //////////////////////////////////////////////////////////////////////////////

  /**
     The current script-argument-index.
  */

  protected int iArgIndex=0;

  //////////////////////////////////////////////////////////////////////////////

  /**
     The Properties-object of this ScriptGenerator.
  */

  private Properties iProps = null;

  //////////////////////////////////////////////////////////////////////////////

  /**
     The PrintWriter-object of this ScriptGenerator.
  */

  private PrintWriter iWriter = null;

  //////////////////////////////////////////////////////////////////////////////

  /**
     The ImageCommand-object of this ScriptGenerator.
  */

  private ImageCommand iCmd = null;

  //////////////////////////////////////////////////////////////////////////////

  /**
     The Operation-object of this ScriptGenerator.
  */

  private Operation iOp = null;

  //////////////////////////////////////////////////////////////////////////////

  /**
     Generate the script.
  */

  public void createScript() {
    iIndent="";
    iArgIndex=0;
    writeHeader();
    writeCommand();
    writeOperation();
    getWriter().flush();
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Write the header of the script. This method must be implemented by
     subclasses.
  */

  protected void writeHeader() {
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the search-path in a plattform-dependent way.
  */

  protected String getSearchPath() {
    String globalPath=getProperties().getProperty("im4java.globalSearchPath");
    String localPath=getProperties().getProperty("im4java.localSearchPath");
    
    String path=null;
    if (localPath.length() > 0 && globalPath.length() > 0) {
      path=localPath+File.pathSeparator+globalPath;
    } else if (localPath.length() > 0 && globalPath.length() == 0) {
      path=localPath;
    } else if (globalPath.length() > 0) {
      path=globalPath;
    }

    return path;
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Write the ImageCommand to the script-file. This is a default implementation
     which should do for most cases.
  */

  protected void writeCommand() {
    PrintWriter writer=getWriter();
    writer.print(iProps.getProperty("im4java.cmd"));
    writer.println(ESC_EOL);
    iIndent = iIndent+"  ";
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the token as a script-argument. Normally, the argument token is
     only part of the script-argument if it contains a [wxh+x+y]-read-spec.

     <p>This method must be implemented by subclasses.</p>
  */

  protected String getScriptArg(String pToken) {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Quote the given string. This method must be implemented by subclasses.
  */

  protected String quote(String pString) {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Write the Operation to the script-file.
  */

  protected void writeOperation() {
    LinkedList<String> cmdArgs= getOperation().getCmdArgs();

    // initialize buffers
    iLineBuffer = new StringBuilder();
    StringBuilder opBuffer = new StringBuilder();

    for (String token:cmdArgs) {
      char firstChar=token.charAt(0);
      if (firstChar=='-' || firstChar=='+') {
	// a new command-option. Flush line if necessary and reset buffer.
	flushBuffer(opBuffer);
	opBuffer = new StringBuilder(token);
      } else {
	// arguments for an option. Just append to the buffer.
	if (opBuffer.length() > 0) {
	  opBuffer.append(" ");
	}

        // handle dynamic images as script-arguments
	if (token.startsWith(Operation.IMG_PLACEHOLDER)) {
	  opBuffer.append(getScriptArg(token));

	  // special case sub-operation: start new line and indent
	} else if (firstChar=='(') {
	  flushBuffer(opBuffer);
	  flushLine(false);
	  opBuffer = new StringBuilder("\"(\"");
	  flushBuffer(opBuffer);
	  flushLine(false);
	  opBuffer = new StringBuilder();
	  iIndent = iIndent+"  ";

	  // end of su-operation: start new line and reduce indentation
	} else if (firstChar==')') {
	  flushBuffer(opBuffer);
	  flushLine(false);
	  iIndent = iIndent.substring(0,iIndent.length()-2);
	  opBuffer = new StringBuilder("\")\"");
	  flushBuffer(opBuffer);
	  flushLine(false);
	  opBuffer = new StringBuilder();

	  // standard case: add token to buffer
	} else {
	  opBuffer.append(quote(token));
      opBuffer.append(" ");
    }
      }
    }
    flushBuffer(opBuffer);
    flushLine(true);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Flush the given buffer to the line-buffer.
  */

  private void flushBuffer(StringBuilder pBuf) {
    if (iIndent.length()+iLineBuffer.length()+pBuf.length()+2 > LINE_SIZE) {
      // dump the line and re-init the line-buffer
      flushLine(false);
      iLineBuffer.append(pBuf);
	/* Without the following line (appending the empty space) sometimes it ignores spacing between previous variable
	 * and the next command. Example:- -resize "1024x681"-format "jpg".  notice no space between "1024x681" and -format.
	 */
      iLineBuffer.append(" "); 
    } else {
      // the given buffer still fits, so append to the line
      iLineBuffer.append(pBuf);
      iLineBuffer.append(" ");
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Flush the line to the writer.
  */

  private void flushLine(boolean last) {
    PrintWriter writer=getWriter();
    writer.print(iIndent);
    writer.print(iLineBuffer.toString());
    if (last) {
      writer.println("\n");
    } else {
      writer.println(ESC_EOL);
      iLineBuffer = new StringBuilder();
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Intialize the generator.
  */

  public void init(PrintWriter pWriter, Operation pOp, Properties pProps) {
    iWriter=pWriter;
    iOp=pOp;
    iProps=pProps;
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the PrintWriter-object.
  */

  public PrintWriter getWriter() {
    return iWriter;
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the Operation-object.
  */

  public Operation getOperation() {
    return iOp;
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
     Return the Properties-object.
  */

  public Properties getProperties() {
    return iProps;
  }

}
