/*
 * $Id$
 *
 * This file is part of McIDAS-V
 *
 * Copyright 2007-2009
 * Space Science and Engineering Center (SSEC)
 * University of Wisconsin - Madison
 * 1225 W. Dayton Street, Madison, WI 53706, USA
 * http://www.ssec.wisc.edu/mcidas
 * 
 * All Rights Reserved
 * 
 * McIDAS-V is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * McIDAS-V is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package edu.wisc.ssec.mcidasv.control;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import ucar.unidata.data.DataChoice;
import ucar.unidata.data.DataSelection;
import ucar.unidata.idv.ControlDescriptor;
import ucar.unidata.idv.IntegratedDataViewer;
import ucar.unidata.idv.control.ControlWidget;
import ucar.unidata.util.ColorTable;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.Range;
import ucar.visad.display.DisplayMaster;
import ucar.visad.display.DisplayableData;

import visad.DataReference;
import visad.DataReferenceImpl;
import visad.FlatField;
import visad.RealTuple;
import visad.VisADException;
import visad.georef.MapProjection;

import edu.wisc.ssec.mcidasv.Constants;
import edu.wisc.ssec.mcidasv.data.hydra.HydraRGBDisplayable;
import edu.wisc.ssec.mcidasv.data.hydra.MultiSpectralDataSource;
import edu.wisc.ssec.mcidasv.data.hydra.MultiSpectralData;
import edu.wisc.ssec.mcidasv.data.hydra.SpectrumAdapter;
import edu.wisc.ssec.mcidasv.display.hydra.MultiSpectralDisplay;
import edu.wisc.ssec.mcidasv.probes.ProbeEvent;
import edu.wisc.ssec.mcidasv.probes.ProbeListener;
import edu.wisc.ssec.mcidasv.probes.ReadoutProbe;
import edu.wisc.ssec.mcidasv.probes.ReadoutProbeDeux;

public class MultiSpectralControl extends HydraControl {

    private static final String PROBE_ID = "readout.probe";

    private String PARAM = "BrightnessTemp";

    private static final int DEFAULT_FLAGS = 
        FLAG_COLORTABLE | FLAG_ZPOSITION;

    private MultiSpectralDisplay display;

    private DisplayMaster displayMaster;

    private final JTextField wavenumbox =  
        new JTextField(Float.toString(0f), 12);

    final JTextField minBox = new JTextField(6);
    final JTextField maxBox = new JTextField(6);

    private McIDASVHistogramWrapper histoWrapper;

    private float rangeMin;
    private float rangeMax;

    private final List<Hashtable<String, Object>> spectraProperties = new ArrayList<Hashtable<String, Object>>();
    private final Set<NewSpectrum> spectra = new LinkedHashSet<NewSpectrum>();

    public MultiSpectralControl() {
        super();
        setHelpUrl("idv.controls.hydra.multispectraldisplaycontrol");
    }

    @Override public boolean init(final DataChoice choice)
        throws VisADException, RemoteException 
    {
        Hashtable props = choice.getProperties();
        PARAM = (String) props.get(MultiSpectralDataSource.paramKey);

        List<DataChoice> choices = Collections.singletonList(choice);
        histoWrapper = new McIDASVHistogramWrapper("histo", choices, this);

        Float fieldSelectorChannel =
            (Float)getDataSelection().getProperty(Constants.PROP_CHAN);
        if (fieldSelectorChannel == null)
            fieldSelectorChannel = 0f;

        display = new MultiSpectralDisplay(this);
        display.setWaveNumber(fieldSelectorChannel);

        displayMaster = getViewManager().getMaster();

        //- intialize the Displayable with data before adding to DisplayControl
        DisplayableData imageDisplay = display.getImageDisplay();
        FlatField image = display.getImageData();

        float[] rngvals = (image.getFloats(false))[0];
        float[] minmax = minmax(rngvals);
        rangeMin = minmax[0];
        rangeMax = minmax[1];
        
        imageDisplay.setData(display.getImageData());
        addDisplayable(imageDisplay, DEFAULT_FLAGS);

        // put the multispectral display into the layer controls
        addViewManager(display.getViewManager());

        // tell the idv what options to give the user
        setAttributeFlags(DEFAULT_FLAGS);

        setProjectionInView(true);

        return true;
    }

    @Override public void initDone() {
        try {
            display.showChannelSelector();

            // TODO: this is ugly.
            Float fieldSelectorChannel =
                (Float)getDataSelection().getProperty(Constants.PROP_CHAN);
            if (fieldSelectorChannel == null)
                fieldSelectorChannel = 0f;
            handleChannelChange(fieldSelectorChannel, false);

            displayMaster.setDisplayInactive();

            // this if-else block is detecting whether or not a bundle is
            // being loaded; if true, then we'll have a list of spectra props.
            // otherwise just throw two default spectrums/probes on the screen.
            if (!spectraProperties.isEmpty()) {
                for (Hashtable<String, Object> table : spectraProperties) {
                    Color c = (Color)table.get("color");
//                    Spectrum s = addSpectrum(c);
                    NewSpectrum s = addSpectrum(c);
                    s.setProperties(table);
                }
                spectraProperties.clear();
            } else {
                addSpectrum(new Color(153, 204, 255));
                addSpectrum(Color.MAGENTA);
            }
            pokeSpectra();
            displayMaster.setDisplayActive();
        } catch (Exception e) {
            logException("MultiSpectralControl.initDone", e);
        }
    }

    // this will get called before init() by the IDV's bundle magic.
    public void setSpectraProperties(final List<Hashtable<String, Object>> props) {
        spectraProperties.clear();
        spectraProperties.addAll(props);
    }

    public List<Hashtable<String, Object>> getSpectraProperties() {
        List<Hashtable<String, Object>> props = new ArrayList<Hashtable<String, Object>>();
        for (NewSpectrum s : spectra) {
            props.add(s.getProperties());
        }
        return props;
    }

    public NewSpectrum addSpectrum(final Color color) {
        NewSpectrum spectrum = null;
        try {
            spectrum = new NewSpectrum(this, color);
            spectra.add(spectrum);
        } catch (Exception e) {
            LogUtil.logException("MultiSpectralControl.addSpectrum: error creating new spectrum", e);
        }
        return spectrum;
    }

//    public void removeSpectrum(final Spectrum spectrum) {
//        // add in defensive stuff
//        spectra.remove(spectrum);
//        spectrum.removeValueDisplay();
//    }

    public void removeSpectra() {
        try {
            for (NewSpectrum s : spectra)
                s.removeValueDisplay();
        } catch (Exception e) {
            LogUtil.logException("MultiSpectralControl.removeSpectra: error removing spectrum", e);
        }
    }

    public void pokeSpectra() {
        for (NewSpectrum s : spectra)
            s.pokeValueDisplay();
    }

    @Override public DataSelection getDataSelection() {
        DataSelection selection = super.getDataSelection();
        if (display != null) {
            selection.putProperty(Constants.PROP_CHAN, display.getWaveNumber());
            try {
                selection.putProperty(SpectrumAdapter.channelIndex_name, display.getChannelIndex());
            } catch (Exception e) {
                LogUtil.logException("MultiSpectralControl.getDataSelection", e);
            }
        }
        return selection;
    }

    @Override public void setDataSelection(final DataSelection newSelection) {
        super.setDataSelection(newSelection);
    }

    @Override public MapProjection getDataProjection() {
        MapProjection mp = null;
        Rectangle2D rect =
            MultiSpectralData.getLonLatBoundingBox(display.getImageData());

        try {
            mp = new LambertAEA(rect);
        } catch (Exception e) {
            logException("MultiSpectralControl.getDataProjection", e);
        }

        return mp;
    }

    public static float[] minmax(float[] values) {
      float min =  Float.MAX_VALUE;
      float max = -Float.MAX_VALUE;
      for (int k = 0; k < values.length; k++) {
        float val = values[k];
        if ((val == val) && (val < Float.POSITIVE_INFINITY) && (val > Float.NEGATIVE_INFINITY)) {
          if (val < min) min = val;
          if (val > max) max = val;
        }
      }
      return new float[] {min, max};
    }


    @Override protected Range getInitialRange() throws VisADException,
        RemoteException
    {
        return new Range(rangeMin, rangeMax);
    }

    @Override protected ColorTable getInitialColorTable() {
        return getDisplayConventions().getParamColorTable(PARAM);
    }

    @Override public Container doMakeContents() {
        try {
            JTabbedPane pane = new JTabbedPane();
            pane.add("Display", GuiUtils.inset(getDisplayTab(), 5));
            pane.add("Settings", 
                     GuiUtils.inset(GuiUtils.top(doMakeWidgetComponent()), 5));
            pane.add("Histogram", GuiUtils.inset(GuiUtils.top(getHistogramTabComponent()), 5));
            GuiUtils.handleHeavyWeightComponentsInTabs(pane);
            return pane;
        } catch (Exception e) {
            logException("MultiSpectralControl.doMakeContents", e);
        }
        return null;
    }

    @Override public void doRemove() throws VisADException, RemoteException {
        // forcibly clear the value displays when the user has elected to kill
        // the display. the readouts will persist otherwise.
        removeSpectra();
        super.doRemove();
    }

    @SuppressWarnings("unchecked")
    @Override protected JComponent doMakeWidgetComponent() {
        List<Component> widgetComponents;
        try {
            List<ControlWidget> controlWidgets = new ArrayList<ControlWidget>();
            getControlWidgets(controlWidgets);
            widgetComponents = ControlWidget.fillList(controlWidgets);
//            widgetComponents.add(buildProbePanel());
        } catch (Exception e) {
            LogUtil.logException("Problem building the MultiSpectralControl settings", e);
            widgetComponents = new ArrayList<Component>();
            widgetComponents.add(new JLabel("Error building component..."));
        }

        GuiUtils.tmpInsets = new Insets(4, 8, 4, 8);
        GuiUtils.tmpFill = GridBagConstraints.HORIZONTAL;
        return GuiUtils.doLayout(widgetComponents, 2, GuiUtils.WT_NY, GuiUtils.WT_N);
    }

    private JPanel buildProbePanel() {
        ImageIcon removeIcon =
            GuiUtils.getImageIcon("/auxdata/ui/icons/Delete16.gif");
        JPanel panel = new JPanel();
        panel.add(new JLabel("Readout Probes:"));
//        for (final Spectrum spectrum : spectra) {
//            JButton remove = new JButton(removeIcon);
//            remove.setContentAreaFilled(false);
//            remove.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
//            remove.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent ae) {
//                    
//                }
//            });
//            remove.setToolTipText("Remove the readout probe");
//        }
        return panel;
    }

    protected MultiSpectralDisplay getMultiSpectralDisplay() {
        return display;
    }

    public boolean updateImage(final float newChan) {
        if (!display.setWaveNumber(newChan))
            return false;

        DisplayableData imageDisplay = display.getImageDisplay();

        // mark the color map as needing an auto scale, these calls
        // are needed because a setRange could have been called which 
        // locks out auto scaling.
        ((HydraRGBDisplayable)imageDisplay).getColorMap().resetAutoScale();
        displayMaster.reScale();

        try {
            FlatField image = display.getImageData();
            displayMaster.setDisplayInactive(); //- try to consolidate display transforms
            imageDisplay.setData(image);
            updateHistogramTab();
            pokeSpectra();
            displayMaster.setDisplayActive();
        } catch (Exception e) {
            LogUtil.logException("MultiSpectralControl.updateImage", e);
            return false;
        }

        return true;
    }

    // be sure to update the displayed image even if a channel change 
    // originates from the msd itself.
    @Override public void handleChannelChange(final float newChan) {
        handleChannelChange(newChan, true);
    }

    public void handleChannelChange(final float newChan, boolean update) {
        if (update) {
            if (updateImage(newChan)) {
                wavenumbox.setText(Float.toString(newChan));
            }
        } else {
            wavenumbox.setText(Float.toString(newChan));
        }
    }

    private JComponent getDisplayTab() {
        List<JComponent> compList = new ArrayList<JComponent>();

        if (display.getBandSelectComboBox() == null) {
          final JLabel nameLabel = GuiUtils.rLabel("Wavenumber: ");

          wavenumbox.addActionListener(new ActionListener() {
              public void actionPerformed(ActionEvent e) {
                  String tmp = wavenumbox.getText().trim();
                  updateImage(Float.valueOf(tmp));
              }
          });
          compList.add(nameLabel);
          compList.add(wavenumbox);
        } else {
          final JComboBox bandBox = display.getBandSelectComboBox();
          bandBox.addActionListener(new ActionListener() {
             public void actionPerformed(ActionEvent e) {
                String bandName = (String) bandBox.getSelectedItem();
                Float channel = (Float) display.getMultiSpectralData().getBandNameMap().get(bandName);
                updateImage(channel.floatValue());
             }
          });
          JLabel nameLabel = new JLabel("Band: ");
          compList.add(nameLabel);
          compList.add(bandBox);
        }

        JPanel waveNo = GuiUtils.center(GuiUtils.doLayout(compList, 2, GuiUtils.WT_N, GuiUtils.WT_N));
        return GuiUtils.centerBottom(display.getDisplayComponent(), waveNo);
    }

    private JComponent getHistogramTabComponent() {
        updateHistogramTab();
        JComponent histoComp = histoWrapper.doMakeContents();
        JLabel rangeLabel = GuiUtils.rLabel("Range   ");
        JLabel minLabel = GuiUtils.rLabel("Min");
        JLabel maxLabel = GuiUtils.rLabel("   Max");
        List<JComponent> rangeComps = new ArrayList<JComponent>();
        rangeComps.add(rangeLabel);
        rangeComps.add(minLabel);
        rangeComps.add(minBox);
        rangeComps.add(maxLabel);
        rangeComps.add(maxBox);
        minBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                rangeMin = Float.valueOf(minBox.getText().trim());
                rangeMax = Float.valueOf(maxBox.getText().trim());
                histoWrapper.modifyRange((int)rangeMin, (int)rangeMax);
            }
        });
        maxBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                rangeMin = Float.valueOf(minBox.getText().trim());
                rangeMax = Float.valueOf(maxBox.getText().trim());
                histoWrapper.modifyRange((int)rangeMin, (int)rangeMax);
            }
        });
        JPanel rangePanel =
            GuiUtils.center(GuiUtils.doLayout(rangeComps, 5, GuiUtils.WT_N, GuiUtils.WT_N));
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                resetColorTable();
            }
        });

        JPanel resetPanel = 
            GuiUtils.center(GuiUtils.inset(GuiUtils.wrap(resetButton), 4));

        return GuiUtils.topCenterBottom(histoComp, rangePanel, resetPanel);
    }

    private void updateHistogramTab() {
        try {
            histoWrapper.loadData(display.getImageData());
            org.jfree.data.Range range = histoWrapper.getRange();
            rangeMin = (float)range.getLowerBound();
            rangeMax = (float)range.getUpperBound();
            minBox.setText(Integer.toString((int)rangeMin));
            maxBox.setText(Integer.toString((int)rangeMax));
        } catch (Exception e) {
            logException("MultiSpectralControl.getHistogramTabComponent", e);
        }
    }

    public void resetColorTable() {
        histoWrapper.doReset();
    }

    protected void contrastStretch(final double low, final double high) {
        try {
            org.jfree.data.Range range = histoWrapper.getRange();
            rangeMin = (float)range.getLowerBound();
            rangeMax = (float)range.getUpperBound();
            minBox.setText(Integer.toString((int)rangeMin));
            maxBox.setText(Integer.toString((int)rangeMax));
            setRange(getInitialColorTable().getName(), new Range(low, high));
        } catch (Exception e) {
            logException("MultiSpectralControl.contrastStretch", e);
        }
    }

    private static class NewSpectrum implements ProbeListener {
        private final MultiSpectralControl control;
        private final MultiSpectralDisplay display;
        private final DataReference spectrumRef;
        private ReadoutProbeDeux probe;

        public NewSpectrum(final MultiSpectralControl control, final Color color) throws VisADException, RemoteException {
            this.control = control;
            this.display = control.getMultiSpectralDisplay();
            spectrumRef = new DataReferenceImpl(hashCode() + "_spectrumRef");
            display.addRef(spectrumRef, color);
            probe = new ReadoutProbeDeux(control.getNavigatedDisplay(), display.getImageData());
            probe.setColor(color);
            probe.addProbeListener(this);
        }
        public void probePositionChanged(final ProbeEvent<RealTuple> e) {
            RealTuple position = e.getNewValue();
            try {
                FlatField spectrum = display.getMultiSpectralData().getSpectrum(position);
                spectrumRef.setData(spectrum);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        public void probeColorChanged(final ProbeEvent<Color> e) {
            System.err.println(e);
        }
        public void probeVisibilityChanged(final ProbeEvent<Boolean> e) {
            System.err.println(e);
        }
        public Hashtable<String, Object> getProperties() {
            Hashtable<String, Object> table = new Hashtable<String, Object>();
            return table;
        }
        public void setProperties(final Hashtable<String, Object> table) {
        }
        public void pokeValueDisplay() {
            probe.setField(display.getImageData());
            probe.handleProbeUpdate();
        }
        public void removeValueDisplay() throws VisADException, RemoteException {
            System.err.println("remove!");
            probe.handleProbeRemoval();
            display.removeRef(spectrumRef);
        }
    }

    // TODO(jon): maybe this should be in MultiSpectralDisplay?
//    private static class Spectrum implements ProbeListener {
//        private final MultiSpectralControl control;
//        private final MultiSpectralDisplay display;
//        private final DataReference spectrumRef;
//        private ReadoutProbe probe;
//
//        public Spectrum(final MultiSpectralControl control, final Color color) throws VisADException, RemoteException {
//            this.control = control;
//            this.display = control.getMultiSpectralDisplay();
//            spectrumRef = new DataReferenceImpl(hashCode() + "_spectrumRef");
//            display.addRef(spectrumRef, color);
//            createReadoutProbe(color);
//        }
//
//        public void probePositionChanged(final ProbeEvent<RealTuple> e) {
//            RealTuple position = e.getNewValue();
//            getProperties();
//            try {
//                FlatField spectrum = display.getMultiSpectralData().getSpectrum(position);
//                spectrumRef.setData(spectrum);
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        }
//
//        public void probeColorChanged(final ProbeEvent<Color> e) {
//            Color color = e.getNewValue();
//            try {
//                display.updateRef(spectrumRef, color);
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        }
//
//        // alpha value approach taken so that the z-order in the spectrum 
//        // display is maintained.
//        public void probeVisibilityChanged(final ProbeEvent<Boolean> e) {
//            boolean isVisible = e.getNewValue();
//            Color color = probe.getColor();
//            int alpha = (isVisible) ? 255 : 0;
//            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
//            try {
//                display.updateRef(spectrumRef, color);
//            } catch (Exception ex) {
//                LogUtil.logException("Spectrum.probeVisibilityChanged", ex);
//            }
//        }
//
//        public void pokeValueDisplay() {
//            probe.updateReadoutValue();
//        }
//
//        public void removeValueDisplay() throws VisADException, RemoteException {
//            probe.doRemove();
//            DisplayMaster master = control.getViewManager().getMaster();
//            master.removeDisplayable(probe.getValueDisplay());
//        }
//
//        private void createReadoutProbe(final Color color) {
//            try {
//                IntegratedDataViewer idv = control.getIdv();
//                DataChoice choice = control.getDataChoice();
//                ControlDescriptor descriptor = idv.getControlDescriptor("readout.probe");
//                ReadoutProbe probe = (ReadoutProbe)idv.doMakeControl(Misc.newList(choice), descriptor, (String)null, null, false);
//                probe.doMakeProbe();
//                probe.setDisplay(display);
//                this.probe = probe; // ugh :(
//                probe.addProbeListener(this);
//                probe.setColor(color);
//
//                DisplayMaster displayMaster = control.getViewManager().getMaster();
//                displayMaster.addDisplayable(probe.getValueDisplay());
//            } catch (Exception e) {
//                LogUtil.logException("MultiSpectralControl.createProbe", e);
//            }
//        }
//
//        public Hashtable<String, Object> getProperties() {
//            Hashtable<String, Object> table = new Hashtable<String, Object>();
//            table.put("color", probe.getColor());
//            table.put("visibility", new Boolean(probe.getDisplayVisibility()));
//
//            double[] latLon = probe.getLatLonVals();
//            table.put("lat", new Double(latLon[0]));
//            table.put("lon", new Double(latLon[1]));
//            return table;
//        }
//
//        public void setProperties(final Hashtable<String, Object> table) {
//            Color newColor = (Color)table.get("color");
//            Boolean visibility = (Boolean)table.get("visibility");
//            Double lat = (Double)table.get("lat");
//            Double lon = (Double)table.get("lon");
//
//            try {
//                System.out.println("setProperties: "+newColor);
//                probe.setColor(newColor);
//                display.updateRef(spectrumRef, newColor);
//                double[] xy = probe.getBoxVals(lat, lon);
//                probe.setProbePosition(xy[0], xy[1]);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
}
