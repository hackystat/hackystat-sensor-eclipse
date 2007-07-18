package org.hackystat.sensor.eclipse;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.hackystat.sensor.eclipse.addon.BreakPointerSensor;
import org.hackystat.sensor.eclipse.addon.BuildErrorSensor;
import org.hackystat.sensor.eclipse.addon.DebugSensor;
import org.hackystat.sensor.eclipse.addon.JavaStatementMeter;
import org.hackystat.sensor.eclipse.addon.JavaStructureChangeDetector;
import org.hackystat.sensor.eclipse.preference.PreferenceConstants;
import org.hackystat.sensorshell.SensorProperties;

/**
 * Provides all the necessary sensor initialization and collects data in this singleton class. A
 * client can use one static method to get this instance:
 * <p>
 * A client can set Eclipse sensor by calling <code>getInstance()</code>, ant can use the
 * following process methods: Because of lazy instantiation, any activity was not set until the
 * initial call for <code>getInstance()</code>.
 * </p>
 *
 * @author Hongbing Kou, Takuya Yamashita
 * @version $Id: EclipseSensor.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
 */
public class EclipseSensor {
  /** A singleton instance. */
  private static final EclipseSensor theInstance = new EclipseSensor();

  /** The number of seconds of the state change after which timer will wake up again. */
  private long timerStateChangeInterval = 30;

  /** The number of seconds of the buffer transition after which time will wake up again */
  private long timeBuffTransInterval = 5;

  /** The ITextEdtior instance to hold the active editor's (file's) information. */
  private ITextEditor activeTextEditor;

  /** The active buffer to hold the buffer size of the active file. */
  private int activeBufferSize;

  /** The ITextEditor instance to hold the previous active editor's information */
  private ITextEditor previousTextEditor;

  /**
   * The ITextEdtior instance to hold the de-active editor's (file's) information. to see if several
   * partDeactivated call backs occur in the same time.
   */
  private ITextEditor deactivatedTextEditor;

  /** The threshold buffer size at an file activation to be compared with activeBufferSize */
  private int thresholdBufferSize;

  /** The boolean value to check if an previous file is modified. */
  private boolean isModifiedFromFile;

  /** The boolean value to check if the current opened window is active. */
  private boolean isActivatedWindow;


  /** The SensorShell wrapper class for eclipse. */
  private SensorShellWrapper sensorShellWrapper;

  /** 12 characters hackystat directory key to check if the new sensor shell should be set. */
  private Timer timer;

  /** The TimerTask instance to do the task of the state change when the timer wakes up. */
  private TimerTask stateChangeTimerTask;

  /** The TimerTask instance to do the task of the buffer transitions when the timer wakes up. */
  private TimerTask buffTransTimerTask;

  /** The WindowListerAdapter instance to check if this instance is added or not. */
  private WindowListenerAdapter windowListener;

  /** Build error sensor. */
  private BuildErrorSensor buildErrorSensor;

  /** Keep track of the last buffer trans data in case of the repeation. */
  private String latestBuffTrans = "";

  /**
   * Provides instantiation of SensorProperties, which has information in the sensor.properties
   * file, and executes <code>doCommand</code> to activate sensor. Note that the Eclipse instance
   * is lazily instantiated when static <code>getInstance()</code> was called.
   */
  private EclipseSensor() {
    this.timer = new Timer();
    this.stateChangeTimerTask = new StateChangeTimerTask();
    this.buffTransTimerTask = new BuffTransTimertask();
    
    // Load sensor's setting. 
    this.loadHackystatHostSettings();
    
    
    // Adds this EclipseSensorPlugin instance to IResourceChangeListener
    // so that project event and file save event is notified.
    IWorkspace workspace = ResourcesPlugin.getWorkspace();

    // Adds IResourceChangeListener-implemented ResourceChangeAdapter before checking isEnabled
    // for piemontese sensor because this listener is used in piemontese sensor even though
    // the main sensor.properties does not exist in the <hackystat_home>.
    workspace.addResourceChangeListener(new ResourceChangeAdapter(),
        IResourceChangeEvent.POST_CHANGE);

    // Adds element changed listener to get the corresponding change of refactoring.
    JavaCore.addElementChangedListener(new JavaStructureChangeDetector(this));

    initialize();
  }

  /**
   * Returns the (singleton) EclipseSensor instance. This method is initially called by
   * EclipseSensorPlugin client class for instantiation.
   *
   * @return The (singleton) instance.
   */
  public static EclipseSensor getInstance() {
    return theInstance;
  }

  /**
   * Loads the hackystat specific settings to instantiate sensor shell.
   *
   * @return Sensor properties. 
   */
  protected void loadHackystatHostSettings() {
    // Preference.
    EclipseSensorPlugin plugin = EclipseSensorPlugin.getDefault();
    IPreferenceStore store = plugin.getPreferenceStore();
  
    String host = store.getString(PreferenceConstants.P_HOST);
    String email = store.getString(PreferenceConstants.P_EMAIL);
    String password = store.getString(PreferenceConstants.P_PASSWORD);
    
    SensorProperties sensorProperties = new SensorProperties(host, email, password);
    // Check if the new sensor property file enable sensor to be activated.
    this.sensorShellWrapper = new SensorShellWrapper(sensorProperties);
  }
  
  /**
   * Initializes sensor and JUnitListener instance if the sensor is enabled. Note that JUnit
   * listener instance is added only when the instance is not instantiated.
   */
  public void initialize() {
    // Sets state change time schedule.
    if (this.stateChangeTimerTask.scheduledExecutionTime() == 0) {
      this.timer.schedule(this.stateChangeTimerTask, this.timerStateChangeInterval * 1000,
            this.timerStateChangeInterval * 1000);
    }

    // Sets buffer transition time schedule.
    if (this.buffTransTimerTask.scheduledExecutionTime() == 0) {
      this.timer.schedule(this.buffTransTimerTask, this.timeBuffTransInterval * 1000,
            this.timeBuffTransInterval * 1000);
     }

    registerListeners();
  }

  /**
   * Provide the initialization of the listeners additions. The Window, Part, and Document Listener
   * are added. Note that sensor shell should be instantiated before this method is called because
   * <code>processActivity()</code> method uses sensor shell instance.
   */
  private void registerListeners() {
    IWorkbench workbench = EclipseSensorPlugin.getInstance().getWorkbench();

    // :RESOLVED: JULY 1, 2003
    // Supports the multiple window for sensor collection.
    IWorkbenchWindow[] activeWindows = workbench.getWorkbenchWindows();

    // Check if window listener is not added yet. Otherwise multi instances are notified.
    if (this.windowListener == null) {
      this.windowListener = new WindowListenerAdapter();
      workbench.addWindowListener(new WindowListenerAdapter());
    }

    for (int i = 0; i < activeWindows.length; i++) {
      IWorkbenchPage activePage = activeWindows[i].getActivePage();
      activePage.addPartListener(new PartListenerAdapter());
      IEditorPart activeEditorPart = activePage.getActiveEditor();

      // Adds this EclipseSensorPlugin instance to IDocumentListener
      // only when activeEditorPart is the instance of ITextEditor
      // so that null case is also ignored.
      if (activeEditorPart instanceof ITextEditor) {
        // Sets activeTextEditor. Otherwise a first activated file would not be recorded.
        this.activeTextEditor = (ITextEditor) activeEditorPart;
        // Gets opened file since the initial opened file is not notified from IPartListener.
        String fileName = EclipseSensor.this.getFileName(this.activeTextEditor);

        //TODO: Hackystat 8 wants resource. For example, a file resource will look like
        // file://c:// If Eclipse resource is already in this format, we can simply use
        // it as a resource. 
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put("subtype", "Open");
        keyValueMap.put("unit-type", "file");
        keyValueMap.put("unit-name", EclipseSensor.this.extractFileName(fileName));
        this.addDevEvent("Edit", fileName, keyValueMap, "Opened " + fileName);

        IDocumentProvider provider = this.activeTextEditor.getDocumentProvider();
        IDocument document = provider.getDocument(activeEditorPart.getEditorInput());

        // Initially sets active buffer and threshold buffer.
        // Otherwise a first activated buffer would not be recorded.
        this.activeBufferSize = document.getLength();
        this.thresholdBufferSize = document.getLength();
        document.addDocumentListener(new DocumentListenerAdapter());
      }
    }

    // Handles breakpoint set/unset event.
    IBreakpointManager bpManager = DebugPlugin.getDefault().getBreakpointManager();
    bpManager.addBreakpointListener(new BreakPointerSensor(this));

    // Listens to debug event.
    DebugPlugin.getDefault().addDebugEventListener(new DebugSensor(this));

    // Creates instance to handle build error.
    this.buildErrorSensor = new BuildErrorSensor(this);
  }

  /**
   * Processes development events that occur within the Eclipse browser.  The Browser event
   * classes will invoke this method to process the development event and send the data
   * to Hackystat.  
   * 
   * @param type The type of development event, eg "ProgramUnit:New".
   * @param path The associated path with the event, may be file or directory.
   * @param moreKeyValueMap Additional development event data, eg "unit-name=EclipseSensor".
   * @param message DevEvent message to be used for logging and display.
   */
  public void addDevEvent(String type, String path, 
      Map<String, String> moreKeyValueMap, String message) {
    Map<String, String> keyValueMap = new HashMap<String, String>();
    keyValueMap.put("Tool", "Eclipse");
    keyValueMap.put("SensorDataType", "DevEvent");
    keyValueMap.put("type", type);
    keyValueMap.put("path", path);

    if (moreKeyValueMap != null) {
      keyValueMap.putAll(moreKeyValueMap);
    }
    
    this.sensorShellWrapper.add(keyValueMap, message);
  }
  
  /**
   * Extracts file name from full name with path.
   *
   * @param fileNamePath File name path.
   * @return File name.
   */
  public String extractFileName(String fileNamePath) {
    if (fileNamePath != null && fileNamePath.indexOf("/") > 0) {
      return fileNamePath.substring(fileNamePath.lastIndexOf("/") + 1);
    }
    else {
      return fileNamePath;
    }
  }

  /** Keep track of the latest state change file to avoid sending out repeated data. */
  private String latestStateChangeFileName = "";
  /** Latest file size. */
  private int latestStateChangeFileSize = 0;
  /** Class name to file name map. */
  private HashMap<String, String> class2FileMap = new HashMap<String, String>();

  /**
   * Process the state change activity whose element consists of the (absolute) file name and its
   * buffer size (or file size).
   */
  public void processStateChangeActivity() {
    if (this.activeTextEditor == null) {
      return;
    }
    String activeFileName = this.getFileName(this.activeTextEditor);
    if (!activeFileName.equals("")) {
      int activeBufferSize = this.activeBufferSize;

      // Will not send out data if there is no state change at all.
      if (this.latestStateChangeFileName.equals(activeFileName)
          && this.latestStateChangeFileSize == activeBufferSize) {
        return;
      }

      // Makes up state change data
      //StringBuffer statechangeData = new StringBuffer();
      //statechangeData.append(activeFileName);

      // Calculate test methods and assertions if it is a java file.
      IFileEditorInput fileEditorInput = (IFileEditorInput) this.activeTextEditor.getEditorInput();
      IFile file = fileEditorInput.getFile();
      JavaStatementMeter testCounter = null;
      if (file.exists()) {
        // Adds statechange DevEvent.
        Map<String, String> statechaneKeyValueMap= new HashMap<String, String>();
        statechaneKeyValueMap.put("subtype", "StateChange");
        // Process file size
        String sizeString = String.valueOf(activeBufferSize);
        statechaneKeyValueMap.put("current-size", sizeString);
        
        if (file.getName().endsWith(".java")) {
          // Fully qualified class path
          String className = this.getFullyQualifedClassName();
          statechaneKeyValueMap.put("class-name", className);

          // Measure java file.
          testCounter = measureJavaFile(file);
          
          this.class2FileMap.put(className, file.getLocation().toString());
          String methodCountString = String.valueOf(testCounter.getNumOfMethods());
          statechaneKeyValueMap.put("current-methods", methodCountString);

          String statementCountString = String.valueOf(testCounter.getNumOfStatements());
          statechaneKeyValueMap.put("current-statements", statementCountString);

          // Number of test method and assertion statements.
          if (testCounter.hasTest()) {
            String testMethodCount = String.valueOf(testCounter.getNumOfTestMethods());
            statechaneKeyValueMap.put("current-test-methods", testMethodCount);
            
            String testAssertionCount = String.valueOf(testCounter.getNumOfTestAssertions()); 
            statechaneKeyValueMap.put("current-test-assertions", testAssertionCount);
          }

        }
        
        // Status message for display
        StringBuffer msgBuf = new StringBuffer();
        msgBuf.append("statechange : " + file.getName());
        msgBuf.append(" [").append(statechaneKeyValueMap.get("current-size"));
        msgBuf.append(", ").append(statechaneKeyValueMap.get("class-name"));
        String currentMethods = statechaneKeyValueMap.get("current-methods"); 
        if (currentMethods != null) {
          msgBuf.append(", methods=").append(currentMethods);
        }
        
        String currentStatements = statechaneKeyValueMap.get("current-statements");
        if (currentStatements != null) {
          msgBuf.append(", stms=").append(currentStatements);
        }
        msgBuf.append("]");

        String path = file.getLocation().toString();
        this.addDevEvent("Edit", path, statechaneKeyValueMap, msgBuf.toString());
      }
      this.latestStateChangeFileName = activeFileName;
      this.latestStateChangeFileSize = activeBufferSize;
    }
  }

  /**
   * Calculates java file unit test information.
   *
   * @param file IFile instance to a java file.
   *
   * @return UnitTestCounter instance to this java file.
   */
  private JavaStatementMeter measureJavaFile(IFile file) {
    // Compute number of tests and assertions to this file.
    ICompilationUnit cu = (ICompilationUnit) JavaCore.create(file);
    ASTParser parser = ASTParser.newParser(AST.JLS3);
    parser.setSource(cu);
    parser.setResolveBindings(true);

    ASTNode root = parser.createAST(null);
    JavaStatementMeter counter = new JavaStatementMeter();
    root.accept(counter);

    return counter;
  }

  /**
   * Process the buffer transition to check if the current buffer is visiting a file and if that
   * file is different from the file visited by the buffer during the last wakeup. Its element
   * consists of the the (absolute) file name (or last-time-visited file name) from which an user is
   * visiting, the (absolute) file name (or current-visiting file name) to which the user is
   * visiting, and the modification status of the last-time-visited file.
   */
  public void processBuffTrans() {
    // check if BufferTran property is enable
    if (this.activeTextEditor == null || (this.previousTextEditor == null)) {
      return;
    }
    String toFileName = this.getFileName(this.activeTextEditor);
    String fromFileName = this.getFileName(this.previousTextEditor);
    if (!toFileName.equals(fromFileName) && !toFileName.equals("") && !fromFileName.equals("")) {
      String buffTrans = fromFileName + "->" + toFileName;
      // :RESOVED: 5/21/04 ISSUE:HACK109
      if (!latestBuffTrans.equals(buffTrans)) {
        HashMap<String, String> buffTranKeyValuePairs = new HashMap<String, String>();
        
        buffTranKeyValuePairs.put("subtype", "BufferTransition");
        buffTranKeyValuePairs.put("from-buff-name", fromFileName);
        buffTranKeyValuePairs.put("to-buff-name", toFileName);
        buffTranKeyValuePairs.put("modified", String.valueOf(this.isModifiedFromFile));
        
        String message = "BuffTrans : " + this.extractFileName(fromFileName) + " --> " +
                          this.extractFileName(toFileName);

        this.addDevEvent("Edit", toFileName, buffTranKeyValuePairs, message);
        latestBuffTrans = buffTrans;
      }
    }
  }

  /**
   * Gets the fully qualified class name for an active file. For example, its value is foo.bar.Baz.
   *
   * @return The fully qualified class name. For example,foo.bar.Baz.
   */
  private String getFullyQualifedClassName() {
    if (this.activeTextEditor != null) {
      IFileEditorInput fileEditorInput = (IFileEditorInput) this.activeTextEditor.getEditorInput();
      IFile file = fileEditorInput.getFile();
      if (file.exists() && file.getName().endsWith(".java")) {
        ICompilationUnit compilationUnit = (ICompilationUnit) JavaCore.create(file);
        try {
          return compilationUnit.getTypes()[0].getFullyQualifiedName();
        }
        catch (JavaModelException e) {
          // Ignores this because this occurs
          // if this element does not exist or if an exception occurs
          // while accessing its corresponding resource
        }
      }
    }
    return "";
  }

  /**
   * Gets the fully qualified class name for an active file. For example, its value is foo.bar.Baz.
   *
   * @param file Get fully qualified class file.
   * @return The fully qualified class name. For example,foo.bar.Baz.
   */
  private String getFullyQualifedClassName(IFile file) {
    if (file.exists() && file.getName().endsWith(".java")) {
      ICompilationUnit compilationUnit = (ICompilationUnit) JavaCore.create(file);
      try {
        return compilationUnit.getTypes()[0].getFullyQualifiedName();
      }
      catch (JavaModelException e) {
        // Ignores this because this occurs
        // if this element does not exist or if an exception occurs
        // while accessing its corresponding resource
      }
    }
    return "";
  }

  /**
   * Gets the fully qualified file name, namely, absolute path to the java file with its extension.
   * For example, C:\cvs\foobarproject\src\foo\bar\Bar.java.
   *
   * @param textEditor A ITextEditor instance form which the file name is retrieved.
   * @return The fully qualified file name. For example, C:\cvs\foobarproject\src\foo\bar\Bar.java.
   */
  private String getFileName(ITextEditor textEditor) {
    if (textEditor != null) {
      IEditorInput editorInput = textEditor.getEditorInput();
      if (editorInput != null && editorInput instanceof IFileEditorInput) {
        IFileEditorInput input = (IFileEditorInput) editorInput;
        IFile file = input.getFile();
        if (file != null) {
          IPath location = file.getLocation();
          if (location != null) {
            return location.toString();  
          }          
        }
      }
    }
    return "";
  }

  /**
   * Gets current active editor.
   *
   * @return Current editor.
   */
  public ITextEditor getActiveTextEditor() {
    return this.activeTextEditor;
  }
  
  /**
   * Gets file being edited or was just edited.  
   * 
   * @return File being edited.
   */
  public String getActiveFile() {
    return getFileName(this.activeTextEditor);
  }

  /**
   * Gets fully qualified path from object.
   * 
   * @param className Class name.
   * @return File name path.
   */
  public String getObjectFile(String className) {
    return (String) this.class2FileMap.get(className);  
  }
  
  
  /**
   * Provides the IWindowListener-implemented class to catch the "Browser activated", "Browser
   * closing" event. This inner class is designed to be used by the outer EclipseSensor class.
   *
   * @author Takuya Yamashita
   * @version $Id: EclipseSensor.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
   */
  private class WindowListenerAdapter implements IWindowListener {
    /**
     * Provides manipulation of browser open status due to implement <code>IWindowListener</code>.
     * This method must not be called by client because it is called by platform. Do nothing for
     * Eclipse sensor so far.
     *
     * @param window An IWorkbenchWindow instance to be triggered when a window is activated.
     */
    public void windowActivated(IWorkbenchWindow window) {
      IEditorPart activeEditorPart = window.getActivePage().getActiveEditor();
      if (activeEditorPart instanceof ITextEditor) {
        EclipseSensor.this.activeTextEditor = (ITextEditor) activeEditorPart;
        ITextEditor editor = EclipseSensor.this.activeTextEditor;
        IDocumentProvider provider = editor.getDocumentProvider();
        IDocument document = provider.getDocument(editor.getEditorInput());
        document.addDocumentListener(new DocumentListenerAdapter());
        int activeBufferSize = provider.getDocument(editor.getEditorInput()).getLength();

        // BuffTrans: Copy the new active file size to the threshold buffer size .
        EclipseSensor.this.thresholdBufferSize = activeBufferSize;
        EclipseSensor.this.activeBufferSize = activeBufferSize;
      }
    }

    /**
     * Provides manipulation of browser close status due to implement <code>IWindowListener</code>.
     * This method must not be called by client because it is called by platform. Whenever window is
     * closing, set all the current active file to process file metrics, and then try to send them
     * to server.
     *
     * @param window An IWorkbenchWindow instance to be triggered when a window is closed.
     */
    public void windowClosed(IWorkbenchWindow window) {
      EclipseSensor.this.sensorShellWrapper.send();
    }

    /**
     * Provides manipulation of browser deactivation status due to implement
     * <code>IWindowListener</code>. This method must not be called by client because it is
     * called by platform. Do nothing for Eclipse sensor so far.
     *
     * @param window An IWorkbenchWindow instance to be triggered when a window is deactivated.
     */
    public void windowDeactivated(IWorkbenchWindow window) {
      EclipseSensor.this.isActivatedWindow = false;
      IEditorPart activeEditorPart = window.getActivePage().getActiveEditor();
      if (activeEditorPart instanceof ITextEditor) {
        ITextEditor editor = (ITextEditor) activeEditorPart;
        IDocumentProvider provider = editor.getDocumentProvider();

        // provider could be null if the text editor is closed before this method is called.
        EclipseSensor.this.previousTextEditor = editor;
        int fromFileBufferSize = provider.getDocument(editor.getEditorInput()).getLength();

        // Check if a threshold buffer is either dirty or
        // not the same as the current from file buffer size;
        EclipseSensor.this.isModifiedFromFile = (editor.isDirty()
            || (EclipseSensor.this.thresholdBufferSize != fromFileBufferSize));
      }
    }

    /**
     * Provides manipulation of browser window open status due to implement
     * <code>IWindowListener</code>. This method must not be called by client because it is
     * called by platform. Do nothing for Eclipse sensor so far.
     *
     * @param window An IWorkbenchWindow instance to be triggered when a window is opened.
     */
    public void windowOpened(IWorkbenchWindow window) {
      EclipseSensor.this.registerListeners();
    }
  }

  /**
   * Provides the IPartListener-implemented class to catch "part opened", "part closed" event as
   * well as setting active editor part to the activeTextEditor instance and setting active buffer
   * size of the activeBufferSize field of the EclipseSensor class. Note that methods are called by
   * the following order:
   * <ol>
   * <li>partClosed() or partOpened()</li>
   * <li>partDeactivated()</li>
   * <li>partActivate() if any</li>
   * </ol>
   *
   * @author Takuya Yamashita
   * @version $Id: EclipseSensor.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
   */
  private class PartListenerAdapter implements IPartListener {
    /**
     * Provides manipulation of browser part activation status due to implement
     * <code>IPartListener</code>. This method must not be called by client because it is called
     * by platform. Do nothing for Eclipse sensor so far.
     *
     * @param part An IWorkbenchPart instance to be triggered when a part is activated.
     */
    public void partActivated(IWorkbenchPart part) {

      if (part instanceof ITextEditor) {
        //System.out.println("Sensor : " + part);
        EclipseSensor.this.isActivatedWindow = true;
        EclipseSensor.this.activeTextEditor = (ITextEditor) part;
        ITextEditor editor = EclipseSensor.this.activeTextEditor;
        IDocumentProvider provider = editor.getDocumentProvider();
        IDocument document = provider.getDocument(editor.getEditorInput());
        document.addDocumentListener(new DocumentListenerAdapter());
        int activeBufferSize = provider.getDocument(editor.getEditorInput()).getLength();

        // BuffTrans: Copy the new active file size to the threshold buffer size .
        EclipseSensor.this.thresholdBufferSize = activeBufferSize;
        EclipseSensor.this.activeBufferSize = activeBufferSize;
      }
    }

    /**
     * Provides manipulation of browser part brought-to-top status due to implement
     * <code>IPartListener</code>. This method must not be called by client because it is called
     * by platform. Do nothing for Eclipse sensor so far.
     *
     * @param part An IWorkbenchPart instance to be triggered when a part is brought to top.
     */
    public void partBroughtToTop(IWorkbenchPart part) {
      // not supported in Eclipse Sensor.
    }

    /**
     * Provides manipulation of browser part brought-to-top status due to implement
     * <code>IPartListener</code>. This method must not be called by client because it is called
     * by platform. Whenever part is closing, check whether or not part is the instance of
     * <code>IEditorPart</code>, if so, set process activity as
     * <code>ActivityType.CLOSE_FILE</code> with its absolute path.
     *
     * @param part An IWorkbenchPart instance to be triggered when a part is closed.
     */
    public void partClosed(IWorkbenchPart part) {
      if (part instanceof ITextEditor) {
        String fileName = EclipseSensor.this.getFileName((ITextEditor) part);
        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put("subtype", "Close");
        if (fileName.endsWith(".java")) {
          keyValueMap.put("language", "java");
        }
        keyValueMap.put("unit-type", "file");
        keyValueMap.put("unit-name", EclipseSensor.this.extractFileName(fileName));
        EclipseSensor.this.addDevEvent("Edit", fileName, keyValueMap, fileName);          
        
        IEditorPart activeEditorPart = part.getSite().getPage().getActiveEditor();
        if (activeEditorPart == null) {
          EclipseSensor.this.activeTextEditor = null;
        }
      }
    }

    /**
     * Provides manipulation of browser part deactivation status due to implement
     * <code>IPartListener</code>. This method must not be called by client because it is called
     * by platform. Sets active text editor to be null when the text editor part is deactivated.
     *
     * @param part An IWorkbenchPart instance to be triggered when a part is deactivated.
     */
    public void partDeactivated(IWorkbenchPart part) {
      if (part instanceof ITextEditor && (part != EclipseSensor.this.deactivatedTextEditor)) {
        EclipseSensor.this.deactivatedTextEditor = (ITextEditor) part;
        if (EclipseSensor.this.isActivatedWindow) {
          IEditorPart activeEditorPart = part.getSite().getPage().getActiveEditor();

          // Sets activeTextEdtior to be null only when there is no more active editor.
          // Otherwise the case that the non text editor part is active causes the activeTextEditor
          // to be null so that sensor is not collected after that.
          if (activeEditorPart == null) {
            EclipseSensor.this.activeTextEditor = null;
          }

          // BuffTrans to get the toFrom buffer size.
          ITextEditor editor = (ITextEditor) part;
          IDocumentProvider provider = editor.getDocumentProvider();

          // provider could be null if the text editor is closed before this method is called.
          if (provider != null) {
            EclipseSensor.this.previousTextEditor = editor;
            int fromFileBufferSize = provider.getDocument(editor.getEditorInput()).getLength();

            // Check if a threshold buffer is either dirty or
            // not the same as the current from file buffer size;
            EclipseSensor.this.isModifiedFromFile = (editor.isDirty()
                || (EclipseSensor.this.thresholdBufferSize != fromFileBufferSize));
          }
          else {
            EclipseSensor.this.isModifiedFromFile = false;
            EclipseSensor.this.previousTextEditor = null;
          }
        }
        else {
          EclipseSensor.this.isActivatedWindow = true;
        }
      }
    }

    /**
     * Provides manipulation of browser part brought-to-top status due to implement
     * <code>IPartListener</code>. This method must not be called by client because it is called
     * by platform. Whenever part is opened, check whether or not part is the instance of
     * <code>IEditorPart</code>, if so, set process activity as
     * <code>ActivityType.OPEN_FILE</code> with its absolute path.
     *
     * @param part An IWorkbenchPart instance to be triggered when part is opened.
     */
    public void partOpened(IWorkbenchPart part) {
      if (part instanceof ITextEditor && (part != EclipseSensor.this.activeTextEditor)) {
        EclipseSensor.this.activeTextEditor = (ITextEditor) part;
        String fileName = EclipseSensor.this.getFileName((ITextEditor) part);

        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put("subtype", "Open");
        keyValueMap.put("unit-type", "file");
        keyValueMap.put("unit-name", EclipseSensor.this.extractFileName(fileName));
        EclipseSensor.this.addDevEvent("Edit", fileName, keyValueMap, fileName);          
      }
    }
  }

  /**
   * Provides IDocuementListener-implemented class to set an active buffer size when a document is
   * being edited.
   *
   * @author Takuya Yamashita
   * @version $Id: EclipseSensor.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
   */
  private class DocumentListenerAdapter implements IDocumentListener {
    /**
     * Do nothing right now. Just leave it due to implementation of IDocumentationListener.
     *
     * @param event An event triggered when a document is about to be changed.
     */
    public void documentAboutToBeChanged(DocumentEvent event) {
      // not supported in Eclipse Sensor.
    }

    /**
     * Provides the invocation of DeltaResource.setFileSize(long fileSize) method in order to get
     * buffer size. This method is called every document change since this EclipseSensorPlugin
     * instance was added to IDocumentLister. Since this method, the current buffer size of an
     * active file could be grabbed.
     *
     * @param event An event triggered when a document is changed.
     */
    public void documentChanged(DocumentEvent event) {
      EclipseSensor.this.activeBufferSize = event.getDocument().getLength();
    }
  }

  /**
   * Provides "Open Project, "Close Project", and "Save File" events. Note that this implementing
   * class uses Visitor pattern so that key point to gather these event information is inside the
   * visitor method which is implemented from <code>IResourceDeltaVisitor</code> class.
   *
   * @author Takuya Yamashita
   * @version $Id: EclipseSensor.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
   */
  private class ResourceChangeAdapter implements IResourceChangeListener, IResourceDeltaVisitor {
    /**
     * Provides manipulation of IResourceChangeEvent instance due to implement
     * <code>IResourceChangeListener</code>. This method must not be called by client because it
     * is called by platform when resources are changed.
     *
     * @param event A resource change event to describe changes to resources.
     */
    public void resourceChanged(IResourceChangeEvent event) {
      if (((event.getType() & IResourceChangeEvent.POST_CHANGE) != 0)) {
        //          ||
        //          ((event.getType() & IResourceChangeEvent.POST_AUTO_BUILD) != 0)) {
        try {
          IResourceDelta rootDelta = event.getDelta();

          // Accepts the class instance to let the instance be able to visit resource delta.
          rootDelta.accept(this);
        }
        catch (CoreException e) {
          e.printStackTrace();
        }
      }
    }

    /**
     * Provides visitor pattern due to implement <code>IResourceDeltaVisitor</code>. This method
     * must not be called by client because it is called by EclipseSensorPlugin instance. Note that
     * <code>true</code> is returned if the parameter of IResourceDelta instance has children.
     * <code>false</code> is returned when either Project is opened, closed, or file is saved
     * because no more traverse of children of the IResourceDelta instance is needed.
     *
     * @param delta IResourceDelta instance to contains delta resource.
     * @return true if the resource delta's children should be visited; false if they should be
     *         skipped.
     * @throws CoreException if the visit fails for some reason.
     */
    public boolean visit(IResourceDelta delta) throws CoreException {
      IResource resource = delta.getResource();
      int flag = delta.getFlags();
      int kind = delta.getKind();

      // If there is compilation problem with the current java file then send out the activity data.
      if ((flag & IResourceDelta.MARKERS) != 0) {
        EclipseSensor.this.buildErrorSensor.findBuildProblem(delta);
      }

      // :RESOLVED: 26 May 2003
      // Note that the 147456 enumeration type is not listed in the IResourceDelta static filed.
      // However, its number is generated when Project is either opened or closed so that
      // it is checked in the logical condition.
      if (resource instanceof IProject && ((flag == IResourceDelta.OPEN) || (flag == 147456))) {
        IProject project = resource.getProject();
        String projectName = project.getName();
        String projectLocation = project.getFile(".project").getLocation().toString();

        Map<String, String> keyValueMap = new HashMap<String, String>();
        keyValueMap.put("unit-type", "project");
        keyValueMap.put("unit-name", projectName);

        if (((IProject) resource).isOpen()) {
          keyValueMap.put("subtype", "Open");
          EclipseSensor.this.addDevEvent("Edit", projectLocation, 
            keyValueMap, projectLocation);          
        }
        else {
          keyValueMap.put("subtype", "Close");
          EclipseSensor.this.addDevEvent("Edit", projectLocation, 
            keyValueMap, projectLocation);          
        }
        return false;
      }
      if ((kind == IResourceDelta.CHANGED) && (flag == IResourceDelta.CONTENT)
          && resource instanceof IFile) {
        if (resource.getLocation().toString().endsWith(".java")) {
          IFile file = (IFile) resource;

          Map<String, String> keyValueMap = new HashMap<String, String>();
          
          keyValueMap.put("language", "java");
          keyValueMap.put("unit-type", "file");
          
          // Fully qualified class path
          String className = EclipseSensor.this.getFullyQualifedClassName(file);
          keyValueMap.put("class-name", className);

          // Size of the file in buffer
          String bufferSize = String.valueOf(activeBufferSize);
          keyValueMap.put("current-size", bufferSize);

          // Measure java file.
          JavaStatementMeter testCounter = measureJavaFile(file);
          testCounter = measureJavaFile(file);
          String methodCount = String.valueOf(testCounter.getNumOfMethods());
          keyValueMap.put("current-methods", methodCount);
          
          String statementCount = String.valueOf(testCounter.getNumOfStatements());
          keyValueMap.put("current-statements", statementCount);

          // Number of test method and assertion statements.
          if (testCounter.hasTest()) {
            String testMethodCount = String.valueOf(testCounter.getNumOfTestMethods());
            keyValueMap.put("current-test-methods", testMethodCount);
            
            String testAssertionCount = String.valueOf(testCounter.getNumOfTestAssertions());
            keyValueMap.put("current-test-assertions", testAssertionCount);
          }

          //EclipseSensor.this.eclipseSensorShell.doCommand("Activity", activityData);
          //Construct message to display on Eclipse status bar.
          String fileName = file.getLocation().toString();
          StringBuffer message = new StringBuffer("Save File");
          message.append(" : ").append(EclipseSensor.this.extractFileName(fileName));
          
          keyValueMap.put("subtype", "Save");
          EclipseSensor.this.addDevEvent("Edit", fileName, keyValueMap, message.toString());
        }

        // Visit the children because it is not necessary for the saving file to be only one file.
        return true;
      }
      return true; // visit the children
    }
  }
  
  /**
   * Stops the Eclipse sensor and quits sensorshell.
   *
   */
  public void stop() {
    this.sensorShellWrapper.quit();
  }
}