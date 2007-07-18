package org.hackystat.sensor.eclipse.preference;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.hackystat.sensor.eclipse.EclipseSensorPlugin;
import org.hackystat.sensorbase.client.SensorBaseClient;

/**
 * Implements the preference page for Eclipse Sensor. It does some validation to make sure
 * that hackystat host, email and password are correct. 
 * 
 * @author Hongbing Kou
 * @version $Id: EclipseSensorPreference.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
 */
public class SensorPreferencePage extends FieldEditorPreferencePage 
    implements IWorkbenchPreferencePage {
  /** Host field. */
  private StringFieldEditor hostField; 
  /** Email field. */
  private StringFieldEditor emailField;
  /** Password field. */
  private StringFieldEditor passwordField;
  /** Autoupdate field. */
  private BooleanFieldEditor autoUpdateEditor;
  /** Update site. */
  private StringFieldEditor udpateSiteField;
  
  /** 
   * Instantiate Eclipse plugin preference.
   */
  public SensorPreferencePage() {
    super(GRID);
    
    EclipseSensorPlugin plugin = EclipseSensorPlugin.getDefault();
    setPreferenceStore(plugin.getPreferenceStore());
  }
  
  /** Creates the field editors. Field editors are abstractions of
   * the common GUI blocks needed to manipulate various types
   * of preferences. Each field editor knows how to save and
   * restore itself.
   */
  public void createFieldEditors() {
    // hackystat host
    this.hostField = new StringFieldEditor(
        PreferenceConstants.P_HOST, "&Host", getFieldEditorParent());
    this.hostField.setEmptyStringAllowed(false);
    super.addField(this.hostField);

    // User email
    this.emailField = new StringFieldEditor(
        PreferenceConstants.P_EMAIL, "&Email", getFieldEditorParent());
    this.emailField.setEmptyStringAllowed(false);
    super.addField(this.emailField);

    // Password
    this.passwordField = new StringFieldEditor(
        PreferenceConstants.P_PASSWORD, "&Password", getFieldEditorParent());
    this.passwordField.setEmptyStringAllowed(false);
    super.addField(this.passwordField);

    // Whether or not allow auto update
    this.autoUpdateEditor = new BooleanFieldEditor(
        PreferenceConstants.P_ENABLE_AUTOUPDATE,
        "Enable &AutoUpdate", getFieldEditorParent());
    super.addField(this.autoUpdateEditor);
    
    this.udpateSiteField = new StringFieldEditor(
        PreferenceConstants.P_UPDATE_SITE, "&Update Site", getFieldEditorParent());
    super.addField(this.udpateSiteField);
  }

  /**
   * Initialize the preference page. 
   * 
   * @param workbench Eclipse workbench.
   */
  public void init(IWorkbench workbench) {
    //Initialize the preference store we wish to use
    setPreferenceStore(EclipseSensorPlugin.getDefault().getPreferenceStore());
  }
  
  /**
   * Perform okay to validate host/user/password.
   */
  public boolean performOk() {
    //IPreferenceStore store = super.getPreferenceStore();
    String host = this.hostField.getStringValue();
    if (!SensorBaseClient.isHost(host)) {
      super.setErrorMessage(host + " is an invalid hackystat host!");
      return false;
    }
    
    String email = this.emailField.getStringValue(); 
    //store.getString(PreferenceConstants.P_EMAIL);
    String password = this.passwordField.getStringValue();
    //store.getDefaultString(PreferenceConstants.P_PASSWORD);
    if (!SensorBaseClient.isRegistered(host, email, password)) {
      super.setErrorMessage("Either email or password is incorrect!");
      return false;
    }
    
    
    //TODO: Change sensorsell to let the current setting take effect so that usres 
    // don't have to restart Eclipse.    
    return super.performOk();
  }
}