/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.picsim.picsimlab;


import com.microchip.mplab.mdbcore.OptSupport.PlatformToolContextProperties;
import com.microchip.mplab.mdbcore.assemblies.Assembly;

public class PicsimLabContextProperties extends PlatformToolContextProperties {

    public PicsimLabContextProperties() {
        super();
    }

    @Override
    public void setAssembly(Assembly assembly) {
        super.setAssembly(assembly);

        // TODO: Inspect 'assembly' and assign properties for the options UI.
    }

}