package org.hackystat.sensor.eclipse.addon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.hackystat.core.kernel.sensordata.SensorDataPropertyMap;
import org.hackystat.sensor.eclipse.EclipseSensor;

/**
 * Listens to the java element change events to get incremental work on java objects and collect 
 * refactoring information for test-driven development analysis purpose. It's declared as package
 * private so that it can only be instantiated by Eclise sensor.
 * 
 * @author Hongbing Kou
 * @version $Id: JavaStructureChangeDetector.java,v 1.1.1.1 2005/10/20 23:56:57 johnson Exp $
 */
public class JavaStructureChangeDetector implements IElementChangedListener {
  /** Eclipse sensor which is used to send out hackystat data. */
  private EclipseSensor sensor;

  /**
   * Instantiates the JavaStructureDetector instance with Eclipse sensor.
   * 
   * @param sensor Eclipse sensor.
   */
  public JavaStructureChangeDetector(EclipseSensor sensor) {
    this.sensor = sensor;
  }

  /**
   * Implements the element change response.
   * 
   * @param event Element change event.
   */
  public void elementChanged(ElementChangedEvent event) {
    //IJavaElementDelta jed = event.getDelta().getAffectedChildren()[0];
    IJavaElementDelta[] childrenChanges = event.getDelta().getAffectedChildren(); 
    
    if (childrenChanges != null && childrenChanges.length > 0) {
      javaObjectChange(childrenChanges[0]);
    }
  }
  
  /**
   * Process the editng on java element changes.
   * 
   * @param jed Java element delta change.
   */
  private void javaObjectChange(IJavaElementDelta jed) {
    List additions = new ArrayList();
    List deletions = new ArrayList();
    
    // Traverse the delta change tree for refactoring activity
    traverse(jed, additions, deletions);
    
    //  Gets the location of java file.
    IPath javaFile = jed.getElement().getResource().getLocation();
        
    // No java structure change
    if (additions.isEmpty() && deletions.isEmpty()) {
      return;      
    }
    // Addition, deletion, renaming activity.
    else if (additions.size() == 1 || deletions.size() == 1) {
      if (deletions.size() == 0) {
        processUnary(javaFile, "Add", (IJavaElementDelta) additions.get(0));        
      }
      else if (additions.size() == 0) {
        processUnary(javaFile, "Remove", (IJavaElementDelta) deletions.get(0));
      }
      else if (deletions.size() == 1) {
        IJavaElementDelta fromDelta = (IJavaElementDelta) deletions.get(0);
        IJavaElementDelta toDelta = (IJavaElementDelta) additions.get(0);
        if (fromDelta.getElement().getParent().equals(toDelta.getElement().getParent())) { 
          processRenameRefactor(javaFile, fromDelta, toDelta); 
        }
        else {
          javaFile = fromDelta.getElement().getResource().getLocation();
          processMoveRefactor(javaFile, fromDelta.getElement(), fromDelta.getElement().getParent(), 
              toDelta.getElement().getParent());
        }
      }
    }    
    // Massive addition by copying
    else if (additions.size() > 1) {
      for (Iterator i = additions.iterator(); i.hasNext();) {
        processUnary(javaFile, "Add", (IJavaElementDelta) i.next());
      }
    }
    // Massive block deletion
    else if (deletions.size() > 1) {
      for (Iterator i = deletions.iterator(); i.hasNext();) {
        processUnary(javaFile, "Remove", (IJavaElementDelta) i.next());
      }
    }    
  }

  /**
   * Constructs and sends the java element change data.
   * 
   * @param javaFile Associated file.
   * @param op Operation
   * @param delta Delta change element
   */
  private void processUnary(IPath javaFile, String op, IJavaElementDelta delta) {
    IJavaElement element = delta.getElement();
    
    // Stop if there is no associated element. 
    if (javaFile == null || element == null || element.getResource() == null) {
      return;
    }
    
    String type = retrieveType(element);
    // If type is not field, method, import and class do nothing.
    if (type == null) {
      return;  
    }
    
    if ("Class".equals(type)) {
      javaFile = element.getResource().getLocation();
    }  
    
    // Only deal with java file.
    if (!"java".equals(javaFile.getFileExtension())) {
      return;
    }
    
    String name = retrieveName(element);
    //String toName = 
    if (name != null && !"".equals(name)) {
      // Adds refactoring activity
      List activityData = new ArrayList();
      activityData.add("add");
      activityData.add("type=Refactor");    
      activityData.add("file=" + javaFile.toString());

      SensorDataPropertyMap javaDataMap = new SensorDataPropertyMap();
      javaDataMap.put("op", op);
      javaDataMap.put("type", type);
      javaDataMap.put("name", name);
      activityData.add("pMap=" + javaDataMap.encode());

      StringBuffer msgBuf = new StringBuffer();
      msgBuf.append("Refactor : ");
      msgBuf.append(op + "#" + type + "#" + name);
      
      this.sensor.getEclipseSensorShell().doCommand("Activity", activityData, msgBuf.toString());
      
      // Adds refactoring (rename) DevEvent
      List devEventData = new ArrayList();
      devEventData.add("add");
      devEventData.add("type=Edit");
      devEventData.add("path=" + javaFile.toString());
      
      SensorDataPropertyMap devEventPMap = new SensorDataPropertyMap();
      devEventPMap.put("subtype", "ProgramUnit");
      devEventPMap.put("subsubtype", op);
      devEventPMap.put("language", "java");
      devEventPMap.put("unit-type", type);
      devEventPMap.put("unit-name", name);
      devEventData.add("pMap=" + devEventPMap.encode());
      
      this.sensor.getEclipseSensorShell().doCommand("DevEvent", devEventData, msgBuf.toString());
    }
  }


  /**
   * Constructs and send of the java element change data.
   * 
   * @param javaFile Associated file.
   * @param fromDelta Change from delta.
   * @param toDelta Change to delta.
   */
  private void processRenameRefactor(IPath javaFile, IJavaElementDelta fromDelta, 
      IJavaElementDelta toDelta) {
    String typeName = retrieveType(toDelta.getElement());
    
    if ("Class".equals(typeName)) {
      javaFile = fromDelta.getElement().getResource().getLocation();
    }
    else if ("Package".equals(typeName)) {
      javaFile = fromDelta.getElement().getResource().getLocation();
    }

    // Only deal with java file.
    if (!"java".equals(javaFile.getFileExtension())) {
      return;
    }
    
    String fromName = retrieveName(fromDelta.getElement());
    String toName = retrieveName(toDelta.getElement());
    
    if (fromName != null && !"".equals(fromName) && toName != null && !"".equals(toName)) {
      StringBuffer msgBuf = new StringBuffer();
      msgBuf.append("Refactor : ");
      msgBuf.append("Rename#" + typeName + "#" + fromName + " -> " + toName);

      // Adds refactoring activity
      List activityData = new ArrayList();
      activityData.add("add");
      activityData.add("type=Refactor");    
      activityData.add("file=" + javaFile.toString());
      
      SensorDataPropertyMap javaDataMap = new SensorDataPropertyMap();
      javaDataMap.put("op", "Rename");
      javaDataMap.put("type", typeName);
      javaDataMap.put("fromName", fromName);
      javaDataMap.put("toName", toName);
      activityData.add("pMap=" + javaDataMap.encode());
      
      this.sensor.getEclipseSensorShell().doCommand("Activity", activityData, msgBuf.toString());
      //this.sensor.processActivity("Java Edit", javaFile.toString(), javaDataMap);
      
      // Adds refactoring (rename) DevEvent
      List devEventData = new ArrayList();
      devEventData.add("add");
      devEventData.add("type=Edit");
      devEventData.add("path=" + javaFile.toString());
      
      SensorDataPropertyMap devEventPMap = new SensorDataPropertyMap();
      devEventPMap.put("subtype", "ProgramUnit");
      devEventPMap.put("subsubtype", "Rename");
      devEventPMap.put("language", "java");
      devEventPMap.put("unit-type", typeName);
      devEventPMap.put("from-unit-name", fromName);
      devEventPMap.put("to-unit-name", toName);
      devEventData.add("pMap=" + devEventPMap.encode());
      
      this.sensor.getEclipseSensorShell().doCommand("DevEvent", devEventData, msgBuf.toString());
    }
  }
  
  /**
   * Constructs and send of the java element change data.
   * 
   * @param javaFile Associated file.
   * @param element  Java Element to be moved.
   * @param from Change from element.
   * @param to Change to element.
   */
  private void processMoveRefactor(IPath javaFile, IJavaElement element, 
      IJavaElement from, IJavaElement to) {
    String typeName = retrieveType(element);
    
    // Only deal with java file.
    if (!"java".equals(javaFile.getFileExtension())) {
      return;
    }
    
    String name = retrieveName(element);
    String fromName = retrieveName(from);
    String toName = retrieveName(to);
    
    // Put refactor data together with pound sigh separation and send it to Hackystat 
    // server as activity data.
    if (fromName != null && !"".equals(fromName) && toName != null && !"".equals(toName)) {
      StringBuffer msgBuf = new StringBuffer();
      msgBuf.append("Refactor : ");
      msgBuf.append("Move#" + typeName + "#" + name + "#" + fromName + " -> " + toName);
      
      // Adds refactoring activity
      List activityData = new ArrayList();
      activityData.add("add");
      activityData.add("type=Refactor");    
      activityData.add("file=" + javaFile.toString());
            
      SensorDataPropertyMap javaDataMap = new SensorDataPropertyMap();
      javaDataMap.put("op", "Move");
      javaDataMap.put("type", typeName);
      javaDataMap.put("name", name);
      javaDataMap.put("fromName", fromName);
      javaDataMap.put("toName", toName);
      activityData.add("pMap=" + javaDataMap.encode());
      
      this.sensor.getEclipseSensorShell().doCommand("Activity", activityData, msgBuf.toString());
      
      //this.sensor.processActivity("Java Edit", javaFile.toString(), javaDataMap);    

      // Adds refactoring (move) DevEvent
      List devEventData = new ArrayList();
      devEventData.add("add");
      devEventData.add("type=Edit");
      devEventData.add("path=" + javaFile.toString());
      
      SensorDataPropertyMap devEventRenameMap = new SensorDataPropertyMap();
      devEventRenameMap.put("subtype", "ProgramUnit");
      devEventRenameMap.put("subsubtype", "Move");
      devEventRenameMap.put("language", "java");
      devEventRenameMap.put("unit-type", typeName);
      // return-type not available
      devEventRenameMap.put("from-unit-name", fromName);
      devEventRenameMap.put("to-unit-name", toName);
      devEventData.add("pMap=" + devEventRenameMap.encode());
      
      this.sensor.getEclipseSensorShell().doCommand("DevEvent", devEventData, msgBuf.toString());
    }    
  }
  
  /**
   * Gets the element type.
   * 
   * @param element Java element object
   * @return Element type string (class, method, field or import).
   */
  private String retrieveType(IJavaElement element) {
    int eType = element.getElementType();
    
    switch (eType) {
      case IJavaElement.FIELD:
        return "Field";
      case IJavaElement.METHOD:
        return "Method";
      case IJavaElement.IMPORT_DECLARATION:
      case IJavaElement.IMPORT_CONTAINER:
        return "Import";
      case IJavaElement.COMPILATION_UNIT:
      case IJavaElement.JAVA_PROJECT:
        return "Class";
      case IJavaElement.PACKAGE_FRAGMENT:
        return "Package";
    }
    return null;
  }
  
  /**
   * Gets the element name with signature.
   * 
   * @param element Java element, which could be class, method, field or import.
   * @return Brief element name.
   */
  private String retrieveName(IJavaElement element) {
    String name = element.toString();
    try {
      name = name.substring(0, name.indexOf('['));
    }
    catch (IndexOutOfBoundsException e) {
      System.out.println("Where is the [ ? " + name);
    }
    // Trim off the meaningless "(not open)" string
    int pos = name.indexOf("(not open)");
    if (pos > 0) {
      name = name.substring(0, pos);
    }
    
    // take off the '#' if it exists
    name = name.replace('#', '/');
    
    return name.trim(); 
  }
  
  /**
   * Traverses the delta change tree on java element to look for addition and deletion on
   * java element.
   * 
   * @param delta Delta element change.
   * @param additions Added element holder.
   * @param deletions Deleted element holder.
   */
  private void traverse(IJavaElementDelta delta, List additions, List deletions) {    
    // Saves the addition and deletion.
    if (delta.getKind() == IJavaElementDelta.ADDED) {
       additions.add(delta);
    }
    else if (delta.getKind() == IJavaElementDelta.REMOVED) {
      deletions.add(delta);
    }
    
    // Recursively look for changes on children elements.
    IJavaElementDelta[] children = delta.getAffectedChildren();
    for (int i = 0; i < children.length; i++) {
      traverse(children[i], additions, deletions);
    }
  }
}