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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.File;
import org.eclipse.core.internal.resources.Resource;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.osgi.framework.BundleActivator;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.cmu.cups.androidpermissions.Activator;
import edu.cmu.cups.androidpermissions.astsimple.handler.MethodInvocationVisitor;
import edu.cmu.cups.androidpermissions.astsimple.handler.MethodVisitor;
import edu.cmu.cups.androidpermissions.astsimple.handler.VariableVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.*;
import java.io.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.soap.Node;

public class SampleHandler extends AbstractHandler {


	
	private static final String MARKER_TYPE = "edu.cmu.cs.cups.AndroidAppPrivacyPlugin.permissionProblem";
	//classes that require permissions, used to test classes
	private Map<String,String[]> classmap = new HashMap<String,String[]>();
	//functions that require permissions, used to test function calls
	private Map<String,String[]> functionmap = new HashMap<String,String[]>();
	//permissions that are needed for either classes or functions, used to test manifest permissions
	private Map<String,String[]> permissionmap = new HashMap<String,String[]>();
	//permMark map to keep track of where to place eclipse marks in the manifest if needed
	private Map<String,permMark[]>  projectpermissionmap = new HashMap<String,permMark[]>();
	
	private String curr_project_global = "";  //TODO FIXME horrible use of global var
	
	
	
	private boolean loaded = false;
	private boolean foundManifest = false;

	public Object execute(ExecutionEvent event) throws ExecutionException {
		/*
		classmap = new HashMap<String,String[]>();
		functionmap = new HashMap<String,String[]>();
		permissionmap = new HashMap<String,String[]>();
		projectpermissionmap = new HashMap<String,permMark[]>();
		*/
		
		loadPermissions();
		System.out.println("loaded " + permissionmap.size() + " permissions");
		
		/* print the maps loaded from the database
		System.out.println("function map");
		printmap(functionmap);
		System.out.println("Permission map");
		printmap(permissionmap);
		System.out.println("Class map");
		printmap(classmap);
		*/
		
		// Get the root of the workspace
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		// Get all projects in the workspace
		IProject[] projects = root.getProjects();
		System.out.print("Projects: ");
		for (IProject project : projects) {
				System.out.print(project.getName() +" ");
		}
		System.out.println("");
		// Loop over all projects
		for (IProject project : projects) {
			try {	
				// code to retrieve an java.io.InputStream
				System.out.println("################################################");
				System.out.println("Working in project " + project.getName());
				curr_project_global = project.getName();
			
				clearMarkers(project);
				printProjectInfo(project);
				addMarkersToManifest(project);
				System.out.println("finished with project " + project.getName());
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		printProjectPermissionMap();
		
		/*
		classmap.clear();
		functionmap.clear();
		permissionmap.clear();
		projectpermissionmap.clear();
		*/
		System.out.println("Plugin Finished.");
		
		return null;
	}
	private void clearMarkers(IProject proj){
			
			   String type = "org.eclipse.core.resources.problemmarker";
			   try{
			   proj.deleteMarkers(type, true, IResource.DEPTH_INFINITE);
			   /*
			   IMarker[] markers;
			   System.out.println("deleting old markers...");
			try {
				markers = proj.findMarkers(type, true, IResource.DEPTH_INFINITE);
				 for (IMarker m : markers){
					 System.out.println("Deleting Marker @ " + m.getAttribute(IMarker.LINE_NUMBER, 0));
					   m.delete();
				   }
				   */
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	private void addMarkersToManifest(IProject proj){
		try{
			permMark[] pma = projectpermissionmap.get(proj.getName());
			if(pma != null){
				for(permMark p : pma){
					if(p.isNeeded == false){
				     addMarker(p.file, "This permission is not required!", p.line, IMarker.SEVERITY_ERROR);  
					}
				}
				projectpermissionmap.put(proj.getName(), pma); //have to put them back in case we want to use the map again
			}
		}catch(Exception e){
			System.out.println(e.getMessage());
		}
	}

	private void printProjectInfo(IProject project) throws CoreException,
			JavaModelException {
		if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
		
		
		// Check if we have a Java project
		//TODO better limiting on files
		IResource[] members = project.members();
		
		//first find the manifest so we know what permissions we're dealing with
		System.out.print("Finding Android manifest file....");
		for(int j = 0; j < members.length; j++){
			IResource member = members[j];
			//System.out.print("  member: " + member.getName());
			if(member.getName().equals("AndroidManifest.xml")){  //TODO, full path, can name be changed?
				System.out.println("...found!");
				foundManifest = true;
				SaveOpenFilesHandler sofh = new SaveOpenFilesHandler();
				sofh.showSaveDialog(project);
				
				
				//at this point, this has just been declared a new, valid project, so no project map should exist
	    		if(projectpermissionmap.containsKey(project.getName())){
	    			System.out.println("### Project already had an Android Manifest! (rerun, or duplicate file?)");
	    			//TODO skip project and mark as bad
	    		}

				
				IPath location = member.getLocation();
				if(location != null){
					System.out.println("  located AndroidManifest at: " + location.toOSString());
					IFile myFile = (IFile)member;
					
					if(myFile != null){
						List myPerms = new ArrayList();
						
						//parse the entire manifest with custom sax xml parser
						SAXParserExample spe = new SAXParserExample(myFile);
						
						spe.parseDocument();

						//retrieve perm locations associated with manifest
						permMark[] pma = spe.getPerms();

						for(permMark p : pma){
						     addProjectPermission(project.getName(),p);
						     //test to ensure each permission was located properly
						     //addMarker(p.file, "permission tag", p.line, IMarker.SEVERITY_ERROR);  
						}
						
						/*
			            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			            DocumentBuilder docBuilder = null;
			            org.w3c.dom.Document doc = null;
						try {
							docBuilder = docBuilderFactory.newDocumentBuilder();
						} catch (ParserConfigurationException e1) {
							e1.printStackTrace();
						}
						 try {
								doc =  docBuilder.parse (myFile.getContents());
							} catch (SAXException e1) {
								System.out.println("SAX error " + e1.getMessage() + ".  You manifest must be well formed!");
								continue;
								//e1.printStackTrace();
							} catch (IOException e1) {
								e1.printStackTrace();
							}
							
							doc.getDocumentElement().normalize();
				            //DEBUG System.out.println ("Root element of the doc is " +  doc.getDocumentElement().getNodeName());

				            NodeList listOfPerms = doc.getElementsByTagName("uses-permission");
				            int totalPerms = listOfPerms.getLength();
				            System.out.println("Total no of permissions in manifest : " + totalPerms);

				            for(int s=0; s<listOfPerms.getLength() ; s++){
				            	org.w3c.dom.Node perm = listOfPerms.item(s);
				            	NamedNodeMap attrs = perm.getAttributes();
				            	
				            	 for(int i = 0 ; i< attrs.getLength() ; i++) {
				            	        Attr attribute = (Attr)attrs.item(i);     
				            	        //DEBUG System.out.println("" + attribute.getName()+" = "+attribute.getValue());
				            	        if(attribute.getName().equals("android:name")){
				            	        	System.out.println("  " + attribute.getValue());
				            	    		permMark p = new permMark();
				            	    		//TODO turns out it's very difficult to get line number information 
				            	    		//for w3c xml Nodes...so for now, just mark line 1
				            	    		p.line = 1; 
				            	    		p.file = location.toOSString();
				            	    		p.perm = attribute.getValue();
				            	    		p.isNeeded = false; //will change to true when a function that needs this is encountered
				            	    		addProjectPermission(project.getName(),p);
				            	        }
				            	      }
				            }
				            // DOM parsing doesn't readily allow for retrieving the line number....sigh
				            */
							
						/* the old way with bufferedReader and readLine()
			        	InputStreamReader isr = new InputStreamReader(myFile.getContents());
						BufferedReader br = new BufferedReader(isr);
				        Pattern p = Pattern.compile("android.permission");
						String line = null;
						try {
							//TODO needs to be an XML parser, not readLine
							for(int i = 0; (line = br.readLine()) != null; i++){
								try {
									Matcher m = p.matcher(line);
									if(m.find()){
										System.out.println("Found Android Permission");
									    System.out.println(line);
										addMarker(myFile ,"This permission NOT required!", i, IMarker.SEVERITY_WARNING);
									}else{
										//System.out.println("NOTMATCH");
									}
									
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							System.out.println("br.readLine was null");
						} catch (IOException e) {
							e.printStackTrace();
						}
						System.out.println("X " + line);
						*/
					}
					
				}
			}
		}
		
		//for( IResource member : members){  //eclipse can't handle this syntax
		//if foundManifest then assume it's an Android project
		if(foundManifest){
			System.out.println("Analyzing other files...");
			for(int j = 0; j < members.length; j++){
				IResource member = members[j];
				System.out.print("  project member: " + member.getName());
				String extension = member.getFileExtension();
				if(extension != null){
					System.out.print(" (" + extension +")");
			    }
				
				if((extension != null) && (extension.equals("java"))){
					IPath location = member.getLocation();
					if(location != null){
						System.out.println("");
						System.out.println("  analyzing java file found at: " + location.toOSString());
						IFile myFile = (IFile)member;
						
						// the old way with bufferedReader and readLine()
			        	InputStreamReader isr = new InputStreamReader(myFile.getContents());
						BufferedReader br = new BufferedReader(isr);
				        //Pattern p = Pattern.compile("android.permission");
						String line = null;
						try {
							//TODO needs to be an XML parser, not readLine
							for(int i = 0; (line = br.readLine()) != null; i++){
								try {
									//Matcher m = p.matcher(line);
									//if(m.find()){
									if(checkFunction(line)){
										System.out.println("Found Android Permission-requiring function");
									    System.out.println(line);
										addMarker(myFile ,"This function requires a permission XX!", i, IMarker.SEVERITY_WARNING);
									}else{
										//System.out.println("NOTMATCH");
									}
									
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							System.out.println("br.readLine was null");
						} catch (IOException e) {
							e.printStackTrace();
						}
						System.out.println("X " + line);
					}
				}else if(member.getName().equals("AndroidManifest.xml")){
					System.out.println( "...already processed.");
				}else{
					System.out.println( "...is not a handled type.");
				}
				
			}

			IJavaProject javaProject = JavaCore.create(project);
			printPackageInfos(javaProject);
			
		}else{
			System.out.println("Didn't find an Android manifest, skipping this project.");
		}
		

		}else{
			System.out.println(project.getName() + " is not a java project, skipping...");
		}
		
		
		// Check if we have an XML doc
		//if (project.isNatureEnabled("org.eclipse.jdt.core."))
	}

	private void printPackageInfos(IJavaProject javaProject) throws JavaModelException {
		IPackageFragment[] packages = javaProject.getPackageFragments();
		for (IPackageFragment mypackage : packages) {
			System.out.print(mypackage.getElementName() + " ");
		}
		for (IPackageFragment mypackage : packages) {
			// Package fragments include all packages in the
			// classpath
			// We will only look at the package from the source
			// folder (K_SOURCE)
			// K_BINARY would include also included JARS, e.g.
			// rt.jar
			if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
				if(mypackage.getElementName().length() != 0){
						System.out.println("Package " + mypackage.getElementName());
				}
				foundManifest = false; //reset for each package
				printICompilationUnitInfo(mypackage);
				System.out.println("done with package " + mypackage.getElementName());
			}

		}
	}

	private void printICompilationUnitInfo(IPackageFragment mypackage) throws JavaModelException {
		 // Create a pattern to match breaks
        //Pattern p = Pattern.compile("[,\\s]+");
      //regex style  Pattern p = Pattern.compile("android");
		for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
			System.out.println("Source file " + unit.getElementName());

			Document doc = new Document(unit.getSource());
			System.out.println("Has number of lines: " + doc.getNumberOfLines());
			/* this method is depricated, now we parse the java AST 
			for(int i = 0; i < doc.getNumberOfLines(); i++){
				try {
					int offset = doc.getLineInformation(i).getOffset();
					int len = doc.getLineInformation(i).getLength();
					String line = doc.get(offset,len);
					
					// regex style
					//Matcher m = p.matcher(line);
					//if(m.find()){
					
					if(checkFunction(line)){
						System.out.println("Found known Android API function requiring permissoin XX");
						
						System.out.println("" + i + ": " +	offset + ","+ len + ":" + line);
						
						addMarker((IFile)unit.getResource() ,"This function requires permission XX!", i, IMarker.SEVERITY_WARNING);
					}else{
						//System.out.println("NOTMATCH");
					}
					
				} catch (BadLocationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			*/
			//TODO java only!
			// parse java and find all external calls to test against
			//printIMethods(unit);  //not really intersted in the program's methods, only external ones
			printAST(unit);

		}
	}
	
	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		return (CompilationUnit) parser.createAST(null); // parse
	}
	
	private void printAST(ICompilationUnit unit) throws JavaModelException {
		// Now create the AST for the ICompilationUnits
		CompilationUnit parse = parse(unit);
		
		
		/* this would parse by method: eg "for each class"
		MethodVisitor visitor = new MethodVisitor();
		parse.accept(visitor);
		for (MethodDeclaration method : visitor
				.getMethods()) {
			System.out.println("Method name: "
					+ method.getName()
					+ " Return type: "
					+ method.getReturnType2());
		}
		*/
		
		/* finds variables...
		VariableVisitor vvisitor = new VariableVisitor();
		parse.accept(vvisitor);
		for (VariableDeclarationFragment var : vvisitor.getVariables()) {
			SimpleName name = var.getName();
			System.out.println("Varable Declaration: " + name + " at " + parse.getLineNumber(name.getStartPosition()));
		}
		*/
		
		MethodInvocationVisitor mivisitor = new MethodInvocationVisitor();
		parse.accept(mivisitor);
		for (MethodInvocation mi : mivisitor.getMethods()) {
			SimpleName name = mi.getName();
			String fullyQualifiedClass = "";
			String fullyQualifiedMethod = "";
			//DEBUG System.out.println("Method Invocation: " + name + " at " + parse.getLineNumber(name.getStartPosition()));
			IMethodBinding imb = mi.resolveMethodBinding();
			if(imb != null){
			
			fullyQualifiedClass = imb.getDeclaringClass().getQualifiedName();
			fullyQualifiedMethod = fullyQualifiedClass + "." + imb.getName();
			System.out.println("binding : " + fullyQualifiedMethod);
		
			}
			
			if(checkClass(fullyQualifiedClass)){ //"" + name)){
				System.out.println("Found Android Permission-requiring class at " + unit.getElementName() + ":" + parse.getLineNumber(name.getStartPosition())  );

				IFile myFile =  (IFile)unit.getResource();
				String permissions = getClassPermissions(fullyQualifiedClass);
				if(permissions.length() > 0){
					addMarker(myFile ,"This class requires a permission: " + permissions + " that is not in manifest!!!!!!!!", parse.getLineNumber(name.getStartPosition()), IMarker.SEVERITY_WARNING);
				}
			}
			if(checkFunction(fullyQualifiedMethod)){ //"" + name)){
				System.out.println("Found Android Permission-requiring function at " + unit.getElementName() + ":" + parse.getLineNumber(name.getStartPosition())  );

				IFile myFile =  (IFile)unit.getResource();
				String permissions = getFunctionPermissions(fullyQualifiedMethod);
				if(permissions.length() > 0){
					addMarker(myFile ,"This function requires a permission: " + permissions + " that is not in manifest!!!!!!!!", parse.getLineNumber(name.getStartPosition()), IMarker.SEVERITY_WARNING);
				}
			}
			
		}

	}
	
	private void printIMethods(ICompilationUnit unit) throws JavaModelException {
		IType[] allTypes = unit.getAllTypes();
		for (IType type : allTypes) {
			IMethod[] methods = type.getMethods();
			for (IMethod method : methods) {
				System.out.println("Method name " + method.getElementName());
				System.out.println("Signature " + method.getSignature());
				System.out.println("Return Type " + method.getReturnType());
			}
			
		}
		
	}
	
	private void addMarker(IFile file, String message, int lineNumber,int severity) {
		System.out.println("adding marker to " + file.getName() + " at line " + lineNumber);
		try {
			IMarker marker = file.createMarker("org.eclipse.core.resources.problemmarker");
			marker.setAttribute(IMarker.MESSAGE, message );
			marker.setAttribute(IMarker.SEVERITY, severity);
			marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
			if (lineNumber == -1) {
				lineNumber = 1;
			}
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		} catch (CoreException e) {
			System.out.println("marker error: " + e.getMessage());
		}
	}

		 
	private void loadPermissions(){
		if(!loaded){
			System.out.print("Loading permissions...");
			InputStream inputStream;
			try {
				inputStream = FileLocator.openStream(
					    Activator.getDefault().getBundle(), new Path("resources/API7u1.txt"), false);
	
				InputStreamReader in= new InputStreamReader(inputStream);
			    BufferedReader bin= new BufferedReader(in);
			    
			    String text=null;
			    while((text = bin.readLine()) != null){
			    	try{
			    		//System.out.println("\"" + text.charAt(0) + "\"" + text.length());
				    	if(text.length() > 0){
				    		if(text.charAt(0) != '#'){
				    	
						    	
						    String Parts[] = text.split(";");
						    
						    if(Parts.length != 3){
						    	System.out.println("Malformed config line!  needs three ; sepereated items!");
						    }else{
						    
						    //if starts with "CLASS" add to class map
						    if(Parts[0].equalsIgnoreCase("CLASS")){
						    	String funcs[] = Parts[2].split(",");
				//		    	System.out.println("funcs" + funcs.length);
						    	
						    	for(int i =0; i < funcs.length; i++ ){
						    	    //functionmap.put(Parts[1],Parts[2].split(","));
						    		//System.out.println("parts 1: " + Parts[1] + " " + funcs[i]);
						    		
						    		if(classmap.containsKey(funcs[i])){
						    			String temp[] = new String[classmap.get(funcs[i]).length+1];
						    			temp = classmap.get(funcs[i]);
						    			temp[temp.length-1] = Parts[1];
						    		    classmap.put(funcs[i],temp);
						    		}else{	
						    		   String temp[] = new String[1];
						    		   temp[0] = Parts[1];
						    		   classmap.put(funcs[i], temp);
				//		    		   System.out.println("added " + funcs[i] + "-> " + temp[0]);
						    		 }
						    	}
				//		    	System.out.println("Done");
						    }
						    //if starts with "FUNCTION" add to function map
						    if(Parts[0].equalsIgnoreCase("FUNCTION")){
				//		    	System.out.println("adding function");
						    	String funcs[] = Parts[2].split(",");
				//		    	System.out.println("funcs" + funcs.length);
						    	
						    	for(int i =0; i < funcs.length; i++ ){
						    	    //functionmap.put(Parts[1],Parts[2].split(","));
						    		//System.out.println("parts 1: " + Parts[1] + " " + funcs[i]);
						    		
						    		if(functionmap.containsKey(funcs[i])){
						    			String temp[] = new String[functionmap.get(funcs[i]).length+1];
						    			temp = functionmap.get(funcs[i]);
						    			temp[temp.length-1] = Parts[1];
						    		    functionmap.put(funcs[i],temp);
						    		}else{	
						    		   String temp[] = new String[1];
						    		   temp[0] = Parts[1];
						    		   functionmap.put(funcs[i], temp);
				//		    		   System.out.println("added " + funcs[i] + "-> " + temp[0]);
						    		   
						    		}
						    		
						    	}
						    	
				//		    	System.out.println("Done");
						    }
						    //always add do permissionmap
						    if(Parts[0].equalsIgnoreCase("FUNCTION") || Parts[0].equalsIgnoreCase("CLASS")){
				//		    	System.out.println("adding permission");
						    	permissionmap.put(Parts[1],Parts[2].split(","));
						    }
						    
						    //System.out.println(">>"+text);
						    //System.out.println("" + Parts[0] + "<>" + Parts[1] + "<>" + Parts[2]);
						    	
						    }
				    	}else{
				    		//System.out.println("encountered comment in API config file");
				    	}
					    }//if
			    	}catch(IndexOutOfBoundsException e2){
			    		System.out.println(e2.getMessage() + text);
			    		e2.printStackTrace();
			    		
			    	}
			    }//while
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
			loaded = true;
	    	System.out.println("done");
		}else{
			System.out.println("using already loaded permissions");
		}
	}
	private void printmap(Map somemap){
		Set s = somemap.entrySet();
		Iterator it = s.iterator();
		while(it.hasNext()){
			Map.Entry m = (Map.Entry)it.next();
			String k = (String) m.getKey();
			String v[] = (String[]) m.getValue();
			System.out.println("key: " + k);
			System.out.print("   val: [");
			for(int i = 0; i < v.length; i++){
				System.out.print("" + v[0]);
				if(i< v.length -1){
					System.out.print(", ");
				}
			}
			System.out.println("]");
		}	
		System.out.println("");
	}
	//returns true if the function requires a permission that was not specified in the manifest
	// returns false if not or  if the function is not known by the database this plugin loads
	// as a "side effect" when a function requires a permission, isProjectPermission updates the isNeeded flag
	private boolean checkFunction( String func){
		boolean ret = false;
		
		if(functionmap.containsKey(func)){
			//System.out.print("testing '" + func + "' : " + functionmap.containsKey(func));
			for( String perm : getFunctionPermissionsArray(func)){
			//	System.out.println(perm + " " + "testing" + curr_project_global + " w/ " + perm);
			
				if(isProjectPermission(curr_project_global, perm)){
					System.out.println("project manifest does have " + perm + ", " + func + " use is ok");  //perm was specified in manifest
				}else{
					System.out.println("MISSING PERM "+ perm + " required for method " + func);      //perm was NOT specified
					ret = true;
				}
			}
		}else{
			ret = false;  //unknown function (or at least not mapped to permissions)
		}

		return ret;
	}
	private String[] getFunctionPermissionsArray(String func){
		return functionmap.get(func);
	}
	private String[] getClassPermissionsArray(String func){
		return classmap.get(func);
	}
	private String getFunctionPermissions(String func){
		String ret = "";
		String[] perms = functionmap.get(func);
		for(String s : perms){
			ret += s + " ";
		}
		return ret;
	}
	private String getClassPermissions(String func){
		String ret = "";
		String[] perms = classmap.get(func);
		for(String s : perms){
			ret += s + " ";
		}
		return ret;
	}
	private boolean checkClass(String func){
		boolean ret = false;
		//System.out.print("testing '" + func + "' : " + classmap.containsKey(func));
		if(classmap.containsKey(func)){
			//System.out.print("testing '" + func + "' : " + classmap.containsKey(func));
			for( String perm : getClassPermissionsArray(func)){
			//	System.out.println(perm + " " + "testing" + curr_project_global + " w/ " + perm);
			
				if(isProjectPermission(curr_project_global, perm)){
					System.out.println("project manifest does have " + perm + ", " + func + " use is ok");  //perm was specified in manifest
				}else{
					System.out.println("MISSING PERM "+ perm + " required for class " + func);      //perm was NOT specified
					ret = true;
				}
			}
		}else{
			ret = false;  //unknown function (or at least not mapped to permissions)
		}

		return ret;
	}
	private boolean checkPermission(String perm){
		return permissionmap.containsKey(perm);
	}
	//checks to see if this project already had a required permission in it's manifest file
	//updates the isNeeded flag in permMarks as a side effect
	private boolean isProjectPermission(String project, String perm){
		boolean ret = false;
		Set s = projectpermissionmap.entrySet();
		Iterator it = s.iterator();
		while(it.hasNext()){
			Map.Entry m = (Map.Entry)it.next();
			String k = (String) m.getKey();
			//System.out.println("key: " + k);

			permMark v[] = (permMark[]) m.getValue();

			//System.out.print("   val: [");
			if(k.equals(project)){
				for(int i = 0; i < v.length; i++){
					
					if(v[i].perm.equals(perm)){
						ret = true;
						v[i].isNeeded = true;
						m.setValue(v);
					}
					System.out.print("" + v[i].perm +v[i].isNeeded);
					//if(i< v.length -1){
						//System.out.print(", ");
					//}
				}
			}
			//System.out.println("]");
		}	
		
		System.out.println("");
		//return projectpermissionmap.containsKey(perm);
		return ret;
	}
	private void addProjectPermission(String project, permMark p){
		
		if(projectpermissionmap.containsKey(project)){
			permMark temp[] = new permMark[projectpermissionmap.get(project).length+1];	
			permMark from[] = projectpermissionmap.get(project);

			for(int i=0; i < projectpermissionmap.get(project).length; i++){
				temp[i] = from[i];
			}

			temp[temp.length-1] = p;
			
			projectpermissionmap.put(project,temp);
		}else{
		   permMark temp[] = new permMark[1];
		   temp[0] = p;
		   projectpermissionmap.put(project, temp);
		}
		
		 System.out.println("added permMark " + p.file +":"+ p.line + "-> " + p.perm);
	}
	
	private void printProjectPermissionMap(){
		Set s = projectpermissionmap.entrySet();
		if(s.isEmpty()){
			Iterator it = s.iterator();
			while(it.hasNext()){
				Map.Entry m = (Map.Entry)it.next();
				String k = (String) m.getKey();
				permMark v[] = (permMark[]) m.getValue();
				System.out.println("key: " + k);
				System.out.print("   val: [");
				for(int i = 0; i < v.length; i++){
					System.out.print("line " + v[i].line + "perm" + v[i].perm + "isneeded" + v[i].isNeeded);
					if(i< v.length -1){
						System.out.print(", ");
					}
				}
				System.out.println("]");
			}	
		}
		System.out.println("");
	}	
}
