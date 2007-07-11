package org.hackystat.sensor.eclipse.preference;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.hackystat.sensor.eclipse.EclipseSensorPlugin;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

  /**
   * Initialize the default setting for Hackystat Sensor.
   */
  @Override public void initializeDefaultPreferences() {
    EclipseSensorPlugin plugin = EclipseSensorPlugin.getDefault();
    
    IPreferenceStore store = plugin.getPreferenceStore();
    store.setDefault(PreferenceConstants.P_HOST,  "http://hackystat.ics.hawaii.edu/");
    store.setDefault(PreferenceConstants.P_EMAIL, "food@bar");
    store.setDefault(PreferenceConstants.P_PASSWORD, "Y9Cpd1dK");
    
    store.setDefault(PreferenceConstants.P_ENABLE_AUTOUPDATE, true);
    store.setDefault(PreferenceConstants.P_UPDATE_SITE, 
            "http://hackystat-sensor-eclipse.googlecode.com/svn/trunk/publish/site.xml");
  }

}
