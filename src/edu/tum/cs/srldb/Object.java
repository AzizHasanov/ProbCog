package edu.tum.cs.srldb;
import java.io.PrintStream;
import java.sql.*;
import java.util.*;

import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;
import kdl.prox3.dbmgr.DataTypeEnum;

public class Object extends Item implements IRelationArgument {
	
	protected HashMap<String, Link> links;
	protected String objTypeName;
	protected String constantName = null;
	
	/**
	 * creates an object for the given database; 
	 * Since a type name is not provided but is determined from the actual class name, 
	 * this constructor cannot be used directly  
	 * @param database  the database the object is to be part of (upon commit)
	 */
	protected Object(Database database) {
		this(database, null);
		objTypeName = getClass().getSimpleName();
	}
	
	/**
	 * creates an object of the given type name 
	 * @param database  the database the object is to be part of (upon commit)
	 * @param objTypeName  the type name
	 */
	public Object(Database database, String objTypeName) {
		super(database);
		links = null;
		this.objTypeName = objTypeName;
	}
	
	public Object(Database database, String objTypeName, String constantName) {
		this(database, objTypeName);
		this.constantName = constantName;
	}
	
	/**
	 * links this object to another object
	 * @param linkName  the name of the link/relation
	 * @param otherObj  the object to link to
	 * @return a reference to the newly created link object
	 * @throws DDException 
	 */
	public Link link(String linkName, Object otherObj) throws DDException {
		checkMutable();
		Link link = new Link(database, linkName, this, otherObj);
		if(links == null)
			links = new HashMap<String, Link>();
		links.put(linkName, link);
		return link;
	}
	
	/**
	 * links this object to several other objects
	 * @param linkName  the name of the link/relation
	 * @param otherObjects  the objects to link to 
	 * @return a reference to the newly created link object
	 * @throws DDException 
	 */
	public Link link(String linkName, Object[] otherObjects) throws DDException {
		checkMutable();
		Object[] objs = new Object[1+otherObjects.length];
		objs[0] = this;
		for(int i = 0; i < otherObjects.length; i++)
			objs[i+1] = otherObjects[i];
		Link link = new Link(database, linkName, objs);
		links.put(linkName, link);
		return link;
	}
	
	/**
	 * @return a string, i.e. a constant name, that (uniquely) identifies this object in an MLN database
	 */
	public String getConstantName() {
		if(constantName == null)
			return "O" + objType() + id;
		else
			return constantName;
	}
	
	public String toString() {
		return getConstantName();
	}
	
	public void MLNprintFacts(PrintStream out) throws DDException {		
		for(String attribName : attribs.keySet()) {
			MLNprintFact(attribName, out);
		}
	}
	
	public void MLNprintFact(String attribName, PrintStream out) throws DDException {
		DDAttribute ddAttrib = database.getDataDictionary().getAttribute(attribName); 
		if(ddAttrib.isDiscarded())
			return;
		String strValue = attribs.get(attribName);
		String predicate = Database.stdPredicateName(attribName);
		// check if the attribute is boolean and if so, use a predicate that has no
		// parameters other than the object name
		if(ddAttrib.isBoolean()) {
			BooleanDomain domain = (BooleanDomain) ddAttrib.getDomain(); 
			out.println((!domain.isTrue(strValue) ? "!" : "") + predicate + "(" + getConstantName() + ")");			
		}
		// otherwise use a predicate with two parameters: object name and value
		else {			
			out.println(predicate + "(" + getConstantName() + ", " + Database.stdAttribStringValue(strValue) + ")");
		}
	}
	
	/**
	 * gets the object type name for this object
	 * @return the object type name; class name by default (unless overridden during construction)
	 */
	public String objType() {
		return objTypeName;
	}
	
	/** 
	 * adds the object and all attached links to the database this object is associated with
	 */
	public void commit() {
		addTo(this.database);
	}

	/**
	 * adds the object and all attached links to the given database
	 * @param db
	 */
	public void addTo(Database db) {
		if(db == this.database) // this is a commit
			immutable = true;
		// add object		
		db.addObject(this);
		// add links
		if(links != null) {
			for(Link link : links.values()) {
				link.addTo(db);	
			}			
		}		
	}
	
	public Link getLink(String linkName) {
		if(links == null)
			return null;
		return links.get(linkName);
	}
}
