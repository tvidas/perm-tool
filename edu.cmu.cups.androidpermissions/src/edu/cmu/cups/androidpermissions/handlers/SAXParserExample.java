/*
    Android permission tool for Eclipse
    Copyright (C) 2010 Tim Vidas <tvidas at cmu d0t edu>

    This program is free software; you can redistribute it and/or modify it
    under the terms of the GNU General Public License as published by the Free
    Software Foundation; either version 2 of the License, or (at your option)
    any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
    more details.

    You should have received a copy of the GNU General Public License along with
    this program; if not, write to the Free Software Foundation, Inc., 59 Temple
    Place, Suite 330, Boston, MA 02111-1307 USA
*/

package edu.cmu.cups.androidpermissions.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.resources.IFile;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import org.xml.sax.helpers.DefaultHandler;

public class SAXParserExample extends DefaultHandler{

	private List myPerms;
	
	private String tempVal;
	private IFile fileToParse;
	private Locator locator;
	private List<permMark> permMarkArray;
	
	//to maintain context
//	private Employee tempEmp;
	
	
	public SAXParserExample(IFile ifile){
		fileToParse = ifile;
		myPerms = new ArrayList();
		permMarkArray = new ArrayList<permMark>();
	}
	
	public void runExample() {
		parseDocument();
		printData();
	}
	
	public permMark[] getPerms(){
		
		return permMarkArray.toArray(new permMark[0]);
	}

	public void parseDocument() {
		
		//get a factory
		SAXParserFactory spf = SAXParserFactory.newInstance();
		try {
		
			//get a new instance of parser
			SAXParser sp = spf.newSAXParser();
			
			//parse the file and also register this class for call backs
			sp.parse(fileToParse.getLocation().toString(), this);
			
		}catch(SAXException se) {
			se.printStackTrace();
		}catch(ParserConfigurationException pce) {
			pce.printStackTrace();
		}catch (IOException ie) {
			ie.printStackTrace();
		}
	}

	/**
	 * Iterate through the list and print
	 * the contents
	 */
	private void printData(){
		
		System.out.println("No of Perms '" + myPerms.size() + "'.");
		
		Iterator it = myPerms.iterator();
		while(it.hasNext()) {
			System.out.println(it.next().toString());
		}
	}
	

	//Event Handlers
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		//reset
		tempVal = "";
		if(qName.equalsIgnoreCase("uses-permission")) {
			//create a new instance of employee
			//System.out.println(qName.toString());
			if(attributes.getValue("android:name") != null){

				/*
				System.out.println(attributes.getValue("android:name"));
				System.out.println("Line:" + locator.getSystemId() + ":" + locator.getLineNumber());
				System.out.println("Line:" + fileToParse.toString() + ":" + locator.getLineNumber());
				*/
			
				permMark p = new permMark();
	
	    		p.line = locator.getLineNumber();
	    		p.file = fileToParse; //.getLocation().toOSString();
	    		p.isNeeded = false;
	    		p.perm = attributes.getValue("android:name");
	    		permMarkArray.add(p);
			}
    		/*
    		p.file = location.toOSString();
    		p.perm = attribute.getValue();
    		p.isNeeded = false; //will change to true when a function that needs this is encountered
    		
    		addProjectPermission(project.getName(),p);
    		*/
			
//			tempEmp = new Employee();
//			tempEmp.setType(attributes.getValue("type"));
		}
	}
	

	public void characters(char[] ch, int start, int length) throws SAXException {
		tempVal = new String(ch,start,length);
	}
	
	public void endElement(String uri, String localName, String qName) throws SAXException {

		if(qName.equalsIgnoreCase("uses-permission")) {
			//add it to the list
//			System.out.println(qName.toString());
//			myEmpls.add(tempEmp);
//			myPerms.add(qName);
		
		}
		
	}
	
	/*
	public static void main(String[] args){
		SAXParserExample spe = new SAXParserExample();
		spe.runExample();
	}
	*/
    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }
}




