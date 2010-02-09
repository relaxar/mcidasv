/*
 * $Id$
 *
 * This file is part of McIDAS-V
 *
 * Copyright 2007-2010
 * Space Science and Engineering Center (SSEC)
 * University of Wisconsin - Madison
 * 1225 W. Dayton Street, Madison, WI 53706, USA
 * http://www.ssec.wisc.edu/mcidas
 * 
 * All Rights Reserved
 * 
 * McIDAS-V is built on Unidata's IDV and SSEC's VisAD libraries, and
 * some McIDAS-V source code is based on IDV and VisAD source code.  
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

package edu.wisc.ssec.mcidasv.chooser.adde;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.EOFException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.bushe.swing.event.EventBus;
import org.bushe.swing.event.EventSubscriber;
import org.w3c.dom.Element;

import ucar.unidata.idv.chooser.IdvChooser;
import ucar.unidata.idv.chooser.IdvChooserManager;
import ucar.unidata.idv.chooser.adde.AddeServer;
import ucar.unidata.idv.chooser.adde.AddeServer.Group;
import ucar.unidata.idv.ui.BundleTree;
import ucar.unidata.util.DatedThing;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.PreferenceList;
import ucar.unidata.util.StringUtil;
import ucar.unidata.xml.XmlObjectStore;
import ucar.unidata.xml.XmlResourceCollection;
import ucar.unidata.xml.XmlUtil;
import visad.DateTime;
import edu.wisc.ssec.mcidas.adde.AddeURLException;
import edu.wisc.ssec.mcidas.adde.DataSetInfo;
import edu.wisc.ssec.mcidasv.Constants;
import edu.wisc.ssec.mcidasv.McIDASV;
import edu.wisc.ssec.mcidasv.McIdasPreferenceManager;
import edu.wisc.ssec.mcidasv.ParameterSet;
import edu.wisc.ssec.mcidasv.PersistenceManager;
import edu.wisc.ssec.mcidasv.ResourceManager;
import edu.wisc.ssec.mcidasv.servermanager.AddeAccount;
import edu.wisc.ssec.mcidasv.servermanager.EntryStore;
import edu.wisc.ssec.mcidasv.servermanager.EntryTransforms;
import edu.wisc.ssec.mcidasv.servermanager.RemoteAddeEntry;
import edu.wisc.ssec.mcidasv.servermanager.ServerManagerEvent;
import edu.wisc.ssec.mcidasv.servermanager.AddeEntry.EntryType;
import edu.wisc.ssec.mcidasv.ui.ParameterTree;
import edu.wisc.ssec.mcidasv.ui.UIManager;
import edu.wisc.ssec.mcidasv.util.CollectionHelpers;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils.Position;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils.TextColor;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils.Width;

/**
 *
 * @version $Revision$
 */
public class AddeChooser extends ucar.unidata.idv.chooser.adde.AddeChooser implements Constants, EventSubscriber {

    private JComboBox serverSelector;

    /** List of descriptors */
    private PreferenceList descList;

    /** Descriptor/name hashtable */
    protected Hashtable descriptorTable;

    /** Property for the descriptor table */
    public static final String DESCRIPTOR_TABLE = "DESCRIPTOR_TABLE";

    /** Connect button--we need to be able to disable this */
    JButton connectButton = McVGuiUtils.makeImageTextButton(ICON_CONNECT_SMALL, "Connect");

    /** Parameter button--we need to be able to disable this */
    JButton parameterButton =
        McVGuiUtils.makeImageButton("/edu/wisc/ssec/mcidasv/resources/icons/toolbar/document-open22.png",
            this, "doParameters", null, "Load parameter set");

    /** Manage button */
    JButton manageButton =
        McVGuiUtils.makeImageButton("/edu/wisc/ssec/mcidasv/resources/icons/toolbar/preferences-system22.png",
            this, "doManager", null, "Manage servers");

    /** Public button--we need to draw a menu from this */
    JButton publicButton =
        McVGuiUtils.makeImageButton("/edu/wisc/ssec/mcidasv/resources/icons/toolbar/show-layer-controls22.png",
            this, "showGroups", null, "List public datasets");

    /** descriptor label */
    protected JLabel descriptorLabel = new JLabel(getDescriptorLabel()+":");

    /** A widget for the list of dataset descriptors */
    protected JComboBox descriptorComboBox = new JComboBox();

    /** The descriptor names */
    protected String[] descriptorNames;

    /** Flag to keep from infinite looping */
    protected boolean ignoreDescriptorChange = false;

    /**
     * List of JComponent-s that depend on a descriptor being selected
     * to be enabled
     */
    protected ArrayList compsThatNeedDescriptor = new ArrayList();

    /** Selection label text */
    protected String LABEL_SELECT = " -- Select -- ";

    /** Separator string */
    protected static String separator = "----------------";

    /** Name separator string */
    protected static String nameSeparator = " - ";

    /** Reference back to the server manager */
    protected EntryStore serverManager;

    public boolean allServersFlag;

    /** Command for opening up the server manager */
    protected static final String CMD_MANAGER = "cmd.manager";

    private String lastBadServer = "";
    private String lastBadGroup = "";

    private String lastServerName = "";
    private String lastServerGroup = "";
    private String lastServerUser = "";
    private String lastServerProj = "";
    private AddeServer lastServer = new AddeServer("");

    private List<AddeServer> addeServers;

    /** Used for parameter set restore */
    private static final String TAG_FOLDER = "folder";
    private static final String TAG_DEFAULT = "default";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_SERVER = "server";
    private static final String ATTR_GROUP = "GROUP";
    private static final String ATTR_DESCRIPTOR = "DESCRIPTOR";
    private static final String ATTR_POS = "POS";
    private static final String ATTR_DAY = "DAY";
    private static final String ATTR_TIME = "TIME";
    private List restoreTimes = new ArrayList();
    public Element restoreElement;
    private boolean shouldAddSource = false;
    final JCheckBox cb = new JCheckBox("Add source",shouldAddSource);

    /** Maps favorite type to the BundleTree that shows the Manage window for the type */
    private Hashtable parameterTrees = new Hashtable();

    /**
     * Create an AddeChooser associated with an IdvChooser
     *
     * @param mgr The chooser manager
     * @param root The chooser.xml node
     */
    public AddeChooser(IdvChooserManager mgr, Element root) {
        super(mgr, root);
        EventBus.subscribe(ServerManagerEvent.class, this);
        simpleMode = !getProperty(IdvChooser.ATTR_SHOWDETAILS, true);

        loadButton = McVGuiUtils.makeImageTextButton(ICON_ACCEPT_SMALL, getLoadCommandName());
        loadButton.setActionCommand(getLoadCommandName());
        loadButton.addActionListener(this);

        cancelButton = McVGuiUtils.makeImageButton(ICON_CANCEL, "Cancel");
        cancelButton.setActionCommand(GuiUtils.CMD_CANCEL);
        cancelButton.addActionListener(this);
        cancelButton.setEnabled(false);

        serverSelector = getServerSelector();

        serverSelector.setToolTipText("Right click to manage servers");
        serverSelector.getEditor().getEditorComponent().addMouseListener(
            new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    if ( !SwingUtilities.isRightMouseButton(e)) {
                        return;
                    }
                    //        				AddeServer server = getAddeServer();
                    AddeServer server = getAddeServer2(serverSelector, groupSelector);
                    if (server == null) {
                        return;
                    }
                    List<JMenuItem> items = new ArrayList<JMenuItem>();

                    // Set the right-click behavior
                    if (isLocalServer()) {
                        items.add(GuiUtils.makeMenuItem("Manage local ADDE data",
                            AddeChooser.this,
                            "doManager", null));
                    }
                    else {
                        items.add(GuiUtils.makeMenuItem("Manage ADDE servers",
                            AddeChooser.this,
                            "doManager", null));
                    }
                    JPopupMenu popup = GuiUtils.makePopupMenu(items);
                    popup.show(serverSelector, e.getX(), e.getY());
                }
            });
        serverSelector.setMaximumRowCount(16);

        groupSelector.setToolTipText("Right click to manage servers");
        groupSelector.getEditor().getEditorComponent().addMouseListener(
            new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    if ( !SwingUtilities.isRightMouseButton(e)) {
                        return;
                    }
                    //        				AddeServer server = getAddeServer();
                    AddeServer server = getAddeServer2(serverSelector, groupSelector);
                    if (server == null) {
                        return;
                    }
                    List<JMenuItem> items = new ArrayList<JMenuItem>();

                    // Set the right-click behavior
                    if (isLocalServer()) {
                        items.add(GuiUtils.makeMenuItem("Manage local ADDE data",
                            AddeChooser.this, "doManager", null));
                    }
                    else {
                        items.add(GuiUtils.makeMenuItem("Manage ADDE servers",
                            AddeChooser.this, "doManager", null));
                    }
                    JPopupMenu popup = GuiUtils.makePopupMenu(items);
                    popup.show(groupSelector, e.getX(), e.getY());
                }
            });
        groupSelector.setMaximumRowCount(16);

        //        serverManager = ((McIDASV)getIdv()).getServerManager();
        //        serverManager.addManagedChooser(this);
        addServerComp(descriptorLabel);
        //        addServerComp(descriptorComboBox);

        descriptorComboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if ( !ignoreDescriptorChange
                    && (e.getStateChange() == e.SELECTED)) {
                    descriptorChanged();
                }
            }
        });

        // Update the server list and load the saved state
        updateServerList();
        loadServerState();

        // Default to no parameter button unless the overriding class wants one
        hideParameterButton();
    }

    /**
     * Reload the list of servers if they have changed
     */
    public void updateServerList() {
        updateServers();
        updateGroups();
    }

    /**
     * Sort the servers alphabetically
     */
    private Map<String, String> getAccounting(final AddeServer server) {
        Map<String, String> acctInfo = new HashMap<String, String>();
//        if (McIDASV.useNewServerManager) {
            EntryStore entryStore = ((McIDASV)getIdv()).getServerManager();
            String name = server.getName();
            String strType = this.getDataType();
            EntryType type = EntryTransforms.strToEntryType(strType);
            List<Group> groups = server.getGroupsWithType(strType);
            String user = RemoteAddeEntry.DEFAULT_ACCOUNT.getUsername();
            String proj = RemoteAddeEntry.DEFAULT_ACCOUNT.getProject();
            if (!groups.isEmpty()) {
                String group = groups.get(0).getName();
                AddeAccount acct = entryStore.getAccountingFor(name, group, type);
                user = acct.getUsername();
                proj = acct.getProject();
            }
            acctInfo.put("user", user);
            acctInfo.put("proj", proj);
//        } else {
//            return serverManager.getAccounting(server);
//        }
        return acctInfo;
    }

    private List<AddeServer> getManagedServers(final String type) {
//        if (McIDASV.useNewServerManager) {
            EntryStore entryStore = ((McIDASV)getIdv()).getServerManager();
            return entryStore.getIdvStyleEntries(type);
//        } else {
//            if (serverManager == null)
//                serverManager = ((McIDASV)getIdv()).getServerManager();
//            return serverManager.getAddeServers(type);
//        }
    }

    public void updateServers() {
//        if (serverManager == null)
//            serverManager = ((McIDASV)getIdv()).getServerManager();
//        String type = getGroupType();
//        List<AddeServer> managedServers = serverManager.getAddeServers(type);

        String type = getGroupType();
        List<AddeServer> managedServers = getManagedServers(type);
        List<AddeServer> localList = CollectionHelpers.arrList();
        List<AddeServer> remoteList = CollectionHelpers.arrList();
        addeServers = CollectionHelpers.arrList();
        for (AddeServer server : managedServers) {
            if (server.getIsLocal())
                localList.add(server);
            else
                remoteList.add(server);
        }

        // server list doesn't need a separator if there's only remote servers
        if (!localList.isEmpty()) {
        	addeServers.addAll(localList);
        	addeServers.add(new AddeServer(separator));
        }
        Comparator<AddeServer> byServer = new ServerComparator();
        Collections.sort(remoteList, byServer);
        addeServers.addAll(remoteList);

        // always making this call helps to ensure the chooser stays up to date
        // with the server manager.
        GuiUtils.setListData(serverSelector, addeServers);
        if (!addeServers.isEmpty()) {
            serverSelector.setSelectedIndex(0);
        }
    }

    /**
     * Sort the groups alphabetically
     */
    public void updateGroups() {
//        if (groupSelector == null || getAddeServer() == null)
//            return;
        if (groupSelector == null || getAddeServer2(serverSelector, groupSelector) == null)
            return;

        EntryStore servManager = ((McIDASV)getIdv()).getServerManager();

        List<Group> groups = CollectionHelpers.arrList();
        if (isLocalServer()) {
            groups.addAll(servManager.getIdvStyleLocalGroups());
        } else {
            String sel = null;
            Object obj = serverSelector.getSelectedItem();
            if (obj instanceof String) {
                sel = (String)obj;
            } else if (obj instanceof AddeServer) {
                sel = ((AddeServer)obj).getName();
            } else {
                System.err.println("updateGroups: not sure what it is: "+sel+" "+sel.getClass().getName());
                sel = obj.toString();
            }

            EntryType selType = EntryTransforms.strToEntryType(getGroupType());
            groups.addAll(servManager.getIdvStyleRemoteGroups(sel, selType));
        }
        Comparator<Group> byGroup = new GroupComparator();
        Collections.sort(groups, byGroup);
        GuiUtils.setListData(groupSelector, groups);
    }
    
    /**
     * Load any saved server state
     */
    //TODO: Make loadServerState protected in IDV, remove from here
    private void loadServerState() {
        if (addeServers == null) {
            return;
        }
        String id = getId();
        String[] serverState =
            (String[]) getIdv().getStore().get(Constants.PREF_SERVERSTATE + "." + id);
        if (serverState == null) {
            return;
        }
        AddeServer server = AddeServer.findServer(addeServers, serverState[0]);
        if (server == null) {
            return;
        }
        serverSelector.setSelectedItem(server);
        setGroups();
        if (serverState[1] != null) {
            AddeServer.Group group =
                (AddeServer.Group) server.findGroup(serverState[1]);
            if (group != null) {
                groupSelector.setSelectedItem(group);
            }
        }
    }
    
    /**
     * Decide if the server you're asking about is actually a separator
     */
    protected static boolean isSeparator(AddeServer checkServer) {
        if (checkServer != null) {
            if (checkServer.getName().equals(separator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decide if the server you're asking about is local
     */
    protected boolean isLocalServer() {
        return isLocalServer(getAddeServer2(serverSelector, groupSelector));
    }
            
    protected static boolean isLocalServer(AddeServer checkServer) {
        if (checkServer != null)
            return checkServer.getIsLocal();
        return false;
    }
    
    private void setBadServer(String name, String group) {
        if (name == null)
            name = "";
        if (group == null)
            group = "";

        lastBadServer = name;
        lastBadGroup = group;
    }

    private boolean isBadServer(String name, String group) {
        assert lastBadServer != null;
        assert lastBadGroup != null;
        return lastBadServer.equals(name) && lastBadGroup.equals(group);
    }

    private void setLastServer(String name, String group, AddeServer server) {
        if (name == null)
            name = "";
        if (group == null)
            group = "";
        if (server == null) {
            server = new AddeServer(name);
            Group addeGroup = new Group(getDataType(), group, group);
            server.addGroup(addeGroup);
        }
        lastServerName = name;
        lastServerGroup = group;
        lastServer = server;
    }
    
    private boolean isLastServer(String name, String group) {
        assert lastServer != null;
        assert lastServerName != null;
        assert lastServerGroup != null;
        return lastServerName.equals(name) && lastServerGroup.equals(group);
    }

    private AddeServer getAddeServer2(final JComboBox servBox, final JComboBox groupBox) {
//        if (!McIDASV.useNewServerManager) {
//            return getAddeServer();
//        }
//
//        if (servBox == null || groupBox == null)
//            return null;
//
//        Object serv = servBox.getSelectedItem();
//        Object group = groupBox.getSelectedItem();
//        if (serv == null || group == null)
//            return null;
//
//        System.err.println("serv="+serv+" class="+serv.getClass().getName()+" group="+group+" class="+group.getClass().getName());
        AddeServer serv = getAddeServer();
//        return getAddeServer();
        return serv;
    }

    public void onEvent(Object evt) {
//        System.err.println("AddeChooser: onEvent: "+evt);
        this.updateServerList();
    }

    /**
     * Get the selected AddeServer
     *
     * @return the server or null
     */
    protected AddeServer getAddeServer() {
        if (lastServerName != null && lastServerName.equals("unset")) {
//            System.err.println("* getAddeServer: returning null because we're still waiting on the dialog");
            return null;
        }

        Object selected = serverSelector.getSelectedItem();
        if ((selected != null) && (selected instanceof AddeServer)) {
            AddeServer server = (AddeServer)selected;

//            Map<String, String> accounting = serverManager.getAccounting(server);
            Map<String, String> accounting = getAccounting(server);
            lastServerUser = accounting.get("user");
            lastServerProj = accounting.get("proj");
            setLastServer(server.getName(), getGroup(true), server);
            
//            System.err.println("* getAddeServer: returning AddeServer=" + server.getName() + " group=" + server.getGroups()+" user="+lastServerUser+" proj="+lastServerProj + " ugh: " + accounting.get("user") + " " + accounting.get("proj"));
            return (AddeServer)selected;
        } else if ((selected != null) && (selected instanceof String)) {
//            System.err.println("* getAddeServer: whoop whoop="+selected);
//            String name = (String)selected;
//            String group = getGroup(true);
//            if (isBadServer(name, group)) {
////                System.err.println("* getAddeServer: returning null due to text entries being known bad values: name=" + name + " group=" + group);
//                return null;
//            }
//            if (isLastServer(name, group)) {
////                System.err.println("* getAddeServer: returning last server: name=" + lastServer.getName() + " group=" + lastServer.getGroups());
//                return lastServer;
//            }
//            lastServerName = "unset";
//            lastServerGroup = "unset";
//            ServerPreferenceManager serverManager = ((McIdasPreferenceManager)getIdv().getPreferenceManager()).getServerManager();
//            ServerPropertyDialog dialog = new ServerPropertyDialog(null, true, serverManager);
//            Set<Types> defaultTypes = EnumSet.of(ServerPropertyDialog.convertDataType(getDataType()));
//            dialog.setTitle("Add New Server");
//            dialog.showDialog(name, group, defaultTypes);
//            boolean hitApply = dialog.hitApply(true);
//            if (!hitApply) {
////                System.err.println("* getAddeServer: returning null due to cancel request from showDialog");
//                setBadServer(name, group);
//                return null;
//            }
//
//            Set<DatasetDescriptor> added = dialog.getAddedDatasetDescriptors();
//            if (added == null) {
////                System.err.println("* getAddeServer: null list of added servers somehow!");
//                setBadServer(name, getGroup(true));
//                return null;
//            }
//            for (DatasetDescriptor descriptor : added) {
//                updateServerList();
//                AddeServer addedServer = descriptor.getServer();
//                serverSelector.setSelectedItem(addedServer);
////                System.err.println("* getAddeServer: returning newly added AddeServer=" + addedServer.getName() + " group=" + addedServer.getGroups());
//                setLastServer(name, group, addedServer);
//                lastServerUser = descriptor.getUser();
//                lastServerProj = descriptor.getProj();
//                return addedServer;
//            }
        } else if (selected == null) {
//            System.err.println("* getAddeServer: returning null due to null object in selector");
        } else {
//            System.err.println("* getAddeServer: returning null due to unknown object type in selector: " + selected.toString());
        }
        return null;
    }

    /**
     * A utility to add a component to the list of components that
     * need the descriptor
     *
     * @param comp The component
     * @return The component
     */
    protected JComponent addDescComp(JComponent comp) {
        compsThatNeedDescriptor.add(comp);
        return comp;
    }
    
    /**
     * Set LABEL_SELECT from elsewhere
     */
    protected void setSelectString(String string) {
    	LABEL_SELECT = string;
    }
    
    /**
     * Reset the descriptor stuff
     */
    protected void resetDescriptorBox() {
        ignoreDescriptorChange = true;
        descriptorComboBox.setSelectedItem(LABEL_SELECT);
        ignoreDescriptorChange = false;
    }
    
    /**
     * Handle when the user presses the connect button
     *
     * @throws Exception On badness
     */
    public void handleConnect() throws Exception {
        AddeServer server = getAddeServer2(serverSelector, groupSelector);
        if (server == null) {
            return;
        }
        setState(STATE_CONNECTING);
        connectToServer();
        handleUpdate();
    }
    
    protected void handleConnectionError(Exception e) {
    	if (e != null && e.getMessage() != null) {
    		String msg = e.getMessage();
    		int msgPos = msg.indexOf("AddeURLException:");
    		if (msgPos >= 0 && msg.length() > 18) {
    			msg = msg.substring(msgPos + 18);
                setState(STATE_UNCONNECTED);
        		setHaveData(false);
        		resetDescriptorBox();
        		GuiUtils.showDialog("ADDE Error", new JLabel(msg));
        		return;
    		}
    		if (msg.indexOf("Connecting to server:localhost:") >= 0) {
                setState(STATE_UNCONNECTED);
        		setHaveData(false);
        		resetDescriptorBox();
    			GuiUtils.showDialog("ADDE Error", new JLabel("Local server is not responding"));
    			return;
    		}
    	}
    	super.handleConnectionError(e);
    }


    /**
     * Handle the event
     *
     * @param ae The event
     */
    public void actionPerformed(ActionEvent ae) {
        String cmd = ae.getActionCommand();
        if (cmd.equals(CMD_MANAGER)) {
            doManager();
        }
        else {
            super.actionPerformed(ae);
        }
    }

    /**
     * Go directly to the Server Manager
     */
    public void doManager() {
//    	if (isLocalServer()) {
//    		((McIDASV)getIdv()).showAddeManager();
//    		return;
//    	}
    	getIdv().getPreferenceManager().showTab(Constants.PREF_LIST_ADDE_SERVERS);
    }
    
    /**
     * Show the parameter restore tree
     */
    public void doParameters() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem mi = new JMenuItem("Manage...");
        mi.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent ae) {
        		System.out.println(ae);
        		showParameterSetDialog(getParameterSetType());
        	}
        });
        popup.add(mi);
        
        // Add the checkbox to automatically create a data source
        cb.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent ae) {
        		shouldAddSource = cb.isSelected();
        	}
        });
        popup.addSeparator();
        popup.add(cb);

        final PersistenceManager pm = (PersistenceManager)getIdv().getPersistenceManager();
        List<ParameterSet> parameterSets = pm.getAllParameterSets(getParameterSetType());

        for (int i=0; i<parameterSets.size(); i++) {
        	if (i==0) popup.addSeparator();
        	final ParameterSet ps = parameterSets.get(i);
        	
        	// Parameter set at root
        	if (ps.getCategories().size() == 0) {
				mi = new JMenuItem(ps.getName());
				mi.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
        				restoreParameterSet(ps.getElement());
					}
				});
				popup.add(mi);
        	}
        	
        	// Recurse into folders
        	else {
        		// Find or make the menu for the given parameter set
        		JMenu m = getPopupSubMenuForParameterSet(popup, ps);
            	// Create parameter set entry
        		mi = new JMenuItem(ps.getName());
        		mi.addActionListener(new ActionListener() {
        			public void actionPerformed(ActionEvent ae) {
        				restoreParameterSet(ps.getElement());
        			}
        		});
        		m.add(mi);
        	}
        	
        }

        popup.show(parameterButton, 0, (int) parameterButton.getBounds().getHeight());
    }
    
    private JMenu getPopupSubMenuForParameterSet(JPopupMenu popup, final ParameterSet ps) {
    	List<String> menuNames = ps.getCategories();
    	if (menuNames.size() < 1) return null;

    	// Build the complete menu
    	String menuName = menuNames.get(0);
    	menuNames.remove(0);
    	JMenu theMenu = new JMenu();
    	
    	// Look for the menu in popup
    	boolean found = false;
    	for (int i=0; i<popup.getComponentCount(); i++) {
    		Component thisComponent = popup.getComponent(i);
    		if (thisComponent instanceof JMenu && ((JMenu)thisComponent).getText().equals(menuName)) {
    			theMenu = mergeMenuNames((JMenu)thisComponent, menuNames);
    			found = true;
    		}
    	}
    	
    	// Make a new menu, add the root, return the leaf
    	if (!found) {
        	JMenu theRoot = new JMenu(menuName);
        	theMenu = makeMenuRecursive(theRoot, menuNames);
        	popup.add(theRoot);
    	}
    	
    	return theMenu;
    }
    
    /**
     * Make a new recursive menu
     * 
     * @param rootMenu The root menu to add items to
     * @param menuNames List of string names for submenus
     * @return A new JMenu representing the leaf
     */
    private JMenu makeMenuRecursive(JMenu rootMenu, List<String> menuNames) {
    	if (menuNames.size() < 1) return rootMenu;
    	JMenu newMenu = new JMenu(menuNames.get(0));
    	rootMenu.add(newMenu);
    	menuNames.remove(0);
    	return makeMenuRecursive(newMenu, menuNames);
    }
    
    /**
     * Recurse into a menu, returning either a pointer to the designated names path
     *  or a pointer to the leaf menu added by merging new names
     * 
     * @param thisMenu The root menu to merge
     * @param menuNames List of string names to look for
     * @return A new JMenu representing the leaf matched by menuNames
     */
    private JMenu mergeMenuNames(JMenu thisMenu, List<String> menuNames) {
    	if (menuNames.size() < 1) return thisMenu;
    	boolean found = false;
    	String menuName = menuNames.get(0);
    	for (int i=0; i<thisMenu.getItemCount(); i++) {
    		JMenuItem mi = thisMenu.getItem(i);
    		if (!(mi instanceof JMenu)) continue;
    		if (mi.getText().equals(menuName)) {
    	    	menuNames.remove(0);
    			thisMenu = mergeMenuNames((JMenu)mi, menuNames);
    			found = true;
    		}
    	}
    	if (!found) {
    		thisMenu = makeMenuRecursive(thisMenu, menuNames);
    	}
    	return thisMenu;
    }
    
    /**
     * Return the parameter type associated with this chooser.  Override!
     */
    protected String getParameterSetType() {
    	return "adde";
    }
    
    /**
     * Show the parameter set manager
     */
    private void showParameterSetDialog(final String parameterSetType) {
    	ParameterTree tree = (ParameterTree) parameterTrees.get(parameterSetType);
        if (tree == null) {
        	tree = new ParameterTree((UIManager)getIdv().getIdvUIManager() , parameterSetType);
            parameterTrees.put(parameterSetType, tree);
        }
        else {
        	//DAVEP
        	System.out.println("Should refresh the parameter tree here");
        }
        tree.setVisible(true);
    }
    
    /**
     * Clear the selected parameter set
     */
    protected void clearParameterSet() {
    	restoreElement = null;
        restoreTimes = new ArrayList(); 
        shouldAddSource = false;
    }
    
    /**
     * Restore the selected parameter set using element attributes
     * 
     * @param restoreElement
     * @return
     */
    protected boolean restoreParameterSet(Element restoreElement) {
        if (restoreElement == null) return false;
        if (!restoreElement.getTagName().equals("default")) return false;
        
        this.restoreElement = restoreElement;

        boolean oldISCE = ignoreStateChangedEvents;
        ignoreStateChangedEvents = true;
        
        // Restore server
        String server = restoreElement.getAttribute(ATTR_SERVER);
        if (server != null) serverSelector.setSelectedItem(new AddeServer(server));
        
        // Restore group
        String group = restoreElement.getAttribute(ATTR_GROUP);
        if (group != null) groupSelector.setSelectedItem(group);
        
        // Act as though the user hit "connect"
        readFromServer();
        
        // Restore descriptor
        String descriptor = restoreElement.getAttribute(ATTR_DESCRIPTOR);
        if (descriptor != null) {
            Enumeration enumeration = descriptorTable.keys();
            for (int i = 0; enumeration.hasMoreElements(); i++) {
                String key = enumeration.nextElement().toString();
                Object val = descriptorTable.get(key);
                if (descriptor.equals(val)) {
                    descriptorComboBox.setSelectedItem(val + nameSeparator + key);
                    descriptorChanged();
                    break;
                }
            } 
        }

        // Restore date/time
        if (restoreElement.hasAttribute(ATTR_POS)) {
            setDoAbsoluteTimes(false);
        	Integer pos = new Integer(restoreElement.getAttribute(ATTR_POS));
        	if (pos.intValue() >= 0) {
                getRelativeTimesList().setSelectedIndex(pos);
        	}
            restoreTimes = new ArrayList(); 
        }
        else if ((restoreElement.hasAttribute(ATTR_DAY)) && (restoreElement.hasAttribute(ATTR_TIME))) {
        	setDoAbsoluteTimes(true);
        	String dateStr = restoreElement.getAttribute(ATTR_DAY);
        	String timeStr = restoreElement.getAttribute(ATTR_TIME);
        	List dateS = StringUtil.split(dateStr, ",");
        	List timeS = StringUtil.split(timeStr, ",");
        	int numImages = timeS.size();
            restoreTimes = new ArrayList(); 
            try {
        		DateTime dt = new DateTime();
        		dt.resetFormat();
        		String dtformat = dt.getFormatPattern();
        		for (int ix=0; ix<numImages; ix++) {
        			DateTime restoreTime = dt.createDateTime((String)dateS.get(ix) + " " + (String)timeS.get(ix));
            		restoreTimes.add(restoreTime);
        		}
        	} catch (Exception e) {
        		System.out.println("Exception e=" + e);
        		return false;
        	}
        }
        
        System.out.println("Returning from AddeChooser.restoreParameterSet()");
        
        ignoreStateChangedEvents = oldISCE;
        return true;
    }
    
    /**
     * Set the absolute times list. The times list can contain any of the object types
     * that makeDatedObjects knows how to handle, i.e., Date, visad.DateTime, DatedThing, AddeImageDescriptor, etc.
     *
     * @param times List of thinggs to put into absolute times list
     */
    protected void setAbsoluteTimes(List times) {
    	super.setAbsoluteTimes(times);
		restoreAbsoluteTimes();
    }
    
    protected void restoreAbsoluteTimes() {
        List allTimes = makeDatedObjects(super.getAbsoluteTimes());
    	if (restoreTimes.size() > 0 && allTimes.size() > 0) {
    		int[] indices  = new int[restoreTimes.size()];
	        try {
        		DateTime rtdt, atdt;
        		DatedThing at;
        		for (int i = 0; i < restoreTimes.size(); i++) {
            		rtdt = (DateTime)restoreTimes.get(i);
	        		for (int j = 0; j < allTimes.size(); j++) {
	        			at = (DatedThing)allTimes.get(j);
	        			atdt = new DateTime(at.getDate());
	        			if (atdt.equals(rtdt)) {
	        				indices[i] = j;
	        			}
	        			
	        		}
        		}
	    	} catch (Exception e) {
	    		System.out.println("Exception e=" + e);
	    	}
	        setSelectedAbsoluteTimes(indices);
	    }
    }
    
    /**
     * show/hide the parameter restore button
     */
    public void showParameterButton() {
    	parameterButton.setVisible(true);
    }
    public void hideParameterButton() {
    	parameterButton.setVisible(false);
    }

    /**
     * Override and simulate clicking Add Source if requested
     */
    public void setHaveData(boolean have) {
    	super.setHaveData(have);
    	if (have && shouldAddSource) {
        	System.out.println("Adding source at setHaveData");
        	// Even though setHaveData should mean we can go, we can't... wait a few jiffies
        	Misc.runInABit(100, AddeChooser.this, "doClickLoad", null);
    	}
    }

    public void doClickLoad() {
    	loadButton.doClick();
    }
    
    public void showServers() {
        allServersFlag = !allServersFlag;
        XmlObjectStore store = getIdv().getStore();
        store.put(Constants.PREF_SYSTEMSERVERSIMG, allServersFlag);
        store.save();
        updateServers();
        updateGroups();
    }

    protected String getStateString() {
    	int state = getState();
        switch (state) {
            case STATE_CONNECTED: return "Connected to server";
            case STATE_UNCONNECTED: return "Not connected to server";
            case STATE_CONNECTING: return "Connecting to server";
            default: return "Unknown state: " + state;
        }
    }

    /**
     * Disable/enable any components that depend on the server.
     * Try to update the status label with what we know here.
     */
    protected void updateStatus() {
        super.updateStatus();
        if (getState() == STATE_CONNECTED) {
        	lastServer = new AddeServer("");
        	lastServerGroup = "";
        	lastServerName = "";
        	lastServerProj = "";
        	lastServerUser = "";

        	if (!haveDescriptorSelected()) {
        		if (!usingStations() || haveStationSelected()) {
        			//                String name = getDataName().toLowerCase();
        			String name = getDescriptorLabel().toLowerCase();
        			if (StringUtil.startsWithVowel(name)) {
        				setStatus("Please select an " + name);
        			} else {
        				setStatus("Please select a " + name);
        			}
        		}
        	}
        }
        
       	GuiUtils.enableTree(connectButton, getState() != STATE_CONNECTING);
    }
    
    /**
     * Get the data type ID
     *
     * @return  the data type
     */
    public String getDataType() {
        return "ANY";
    }

    /**
     * Check if the server is ok
     *
     * @return status code
     */
    protected int checkIfServerIsOk() {
        try {
            StringBuffer buff = getUrl(REQ_TEXT);
            appendKeyValue(buff, PROP_FILE, FILE_PUBLICSRV);
            URL           url  = new URL(buff.toString());
            URLConnection urlc = url.openConnection();
            InputStream   is   = urlc.getInputStream();
            is.close();
            return STATUS_OK;
        } catch (AddeURLException ae) {
            String aes = ae.toString();
            if (aes.indexOf("Invalid project number") >= 0) {
                LogUtil.userErrorMessage("Invalid project number");
                return STATUS_NEEDSLOGIN;
            }
            if (aes.indexOf("Invalid user id") >= 0) {
                LogUtil.userErrorMessage("Invalid user ID");
                return STATUS_NEEDSLOGIN;
            }
            if (aes.indexOf("Accounting data") >= 0) {
                return STATUS_NEEDSLOGIN;
            }
            if (aes.indexOf("cannot run server 'txtgserv'") >= 0) {
                return STATUS_OK;
            }
            LogUtil.userErrorMessage("Error connecting to server " + getServer() + ":\n"
                                     + ae.getMessage());
            return STATUS_ERROR;
        } catch (ConnectException exc) {
            setState(STATE_UNCONNECTED);
    		setHaveData(false);
    		resetDescriptorBox();
    		String message = "Error connecting to server " + getServer();
    		if (isLocalServer())
    			message += "\n\nLocal servers can be restarted from the\n'Local ADDE Data Manager' in the 'Tools' menu";
            LogUtil.userErrorMessage(message);
    		return STATUS_ERROR;
        } catch (EOFException exc) {
            setState(STATE_UNCONNECTED);
    		setHaveData(false);
    		resetDescriptorBox();
            LogUtil.userErrorMessage("Server " + getServer() + " is not responding");
    		return STATUS_ERROR;
        } catch (Exception exc) {
            logException("Connecting to server: " + getServer(), exc);
            return STATUS_ERROR;
        }
    }

    public boolean canAccessServer() {
//        Set<Types> defaultTypes = EnumSet.of(ServerPropertyDialog.convertDataType(getDataType()));
//        while (true) {
//            int status = checkIfServerIsOk();
//            if (status == STATUS_OK) {
//                break;
//            }
//            if (status == STATUS_ERROR) {
//                setState(STATE_UNCONNECTED);
//                return false;
//            }
//
////            AddeServer server = getAddeServer();
//            AddeServer server = getAddeServer2(serverSelector, groupSelector);
//            Map<String, String> accounting = serverManager.getAccountingFor(server, type)
//
//            String name = server.getName();
//            String group = getGroup();
//            String user = accounting.get("user");
//            String proj = accounting.get("proj");
//
//            ServerPropertyDialog dialog = new ServerPropertyDialog(null, true, serverManager);
//            dialog.setTitle("Edit Server Information");
//            dialog.showDialog(name, group, user, proj, defaultTypes);
//
//            if (!dialog.getAddedDatasetDescriptors().isEmpty()) {
//                System.err.println("verified info: " + dialog.getAddedDatasetDescriptors());
//                break;
//            }
//        }
        return true;
    }

    public Map<String, String> getAccountingInfo() {
        AddeServer server = getAddeServer2(serverSelector, groupSelector);
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (server != null) {
            AddeAccount accounting = serverManager.getAccountingFor(server, getDataType());
            map.put("user", accounting.getUsername());
            map.put("proj", accounting.getProject());
            map.put("server", server.getName());
            map.put("group", getGroup());
        } else {
            map.put("user", RemoteAddeEntry.DEFAULT_ACCOUNT.getUsername());
            map.put("proj", RemoteAddeEntry.DEFAULT_ACCOUNT.getUsername());
            map.put("server", "");
            map.put("group", "");
        }
        return map;
    }

    /**
     * Connect to the server.
     */
    protected void connectToServer() {
    	clearParameterSet();
        setDescriptors(null);
        setDoAbsoluteTimes(false);
        if (!canAccessServer()) {
//            System.err.println("! can't connect! shucks! golly!");
            return;
        } else {
//            System.err.println("! connected..");
        }
        readFromServer();
        saveServerState();
        ignoreStateChangedEvents = true;
        if (descList != null) {
            descList.saveState(groupSelector);
        }
        ignoreStateChangedEvents = false;
    }

    /**
     * Do server connection stuff... override this with type-specific methods
     */
    protected void readFromServer() {
        readDescriptors();
        readTimes();
    }

    /**
     *  Generate a list of image descriptors for the descriptor list.
     */
    protected void readDescriptors() {
        try {
            StringBuffer buff = getGroupUrl(REQ_DATASETINFO, getGroup());
            buff.append("&type=").append(getDataType());
            DataSetInfo  dsinfo = new DataSetInfo(buff.toString());
            descriptorTable = dsinfo.getDescriptionTable();
            String[] names = new String[descriptorTable.size()];
            Enumeration enumeration = descriptorTable.keys();
            for (int i = 0; enumeration.hasMoreElements(); i++) {
                Object thisElement = enumeration.nextElement();
                if (!isLocalServer())
                    names[i] = descriptorTable.get(thisElement).toString() + nameSeparator + thisElement.toString();
                else
                    names[i] = thisElement.toString();
            }
            Arrays.sort(names);
            setDescriptors(names);
            setState(STATE_CONNECTED);
        } catch (Exception e) {
            handleConnectionError(e);
        }
    }

    /**
     * Initialize the descriptor list from a list of names
     *
     * @param names  list of names
     */
    protected void setDescriptors(String[] names) {
        synchronized (WIDGET_MUTEX) {
            ignoreDescriptorChange = true;
            descriptorComboBox.removeAllItems();
            descriptorNames = names;
            if ((names == null) || (names.length == 0)) {
                return;
            }
            descriptorComboBox.addItem(LABEL_SELECT);
            for (int j = 0; j < names.length; j++) {
                descriptorComboBox.addItem(names[j]);
            }
            ignoreDescriptorChange = false;
        }
    }
    
    /**
     * Respond to a change in the descriptor list.
     */
    protected void descriptorChanged() {
        readTimes();
        updateStatus();
    }
    
    /**
     * Check if a descriptor (image type) has been chosen
     *
     * @return  true if an image type has been chosen
     */
    protected boolean haveDescriptorSelected() {
        if ( !GuiUtils.anySelected(descriptorComboBox)) {
            return false;
        }
        return (getDescriptor() != null);
    }
    
    /**
     * Get the selected descriptor.
     *
     * @return  the currently selected descriptor.
     */
    protected String getDescriptor() {
        return getDescriptorFromSelection(getSelectedDescriptor());
    }

    /**
     * Get the descriptor relating to the selection.
     *
     * @param selection   String name from the widget
     *
     * @return  the descriptor
     */
    protected String getDescriptorFromSelection(String selection) {
        if (descriptorTable == null) {
            return null;
        }
        if (selection == null) {
            return null;
        }

        if (!selection.contains(nameSeparator)) {
            return (String)descriptorTable.get(selection);
        }
        else {
	        String[] toks = selection.split(nameSeparator, 2);
	        String key = toks[1].trim();
	        return (String)descriptorTable.get(key);
        }
    }

    /**
     * Get the selected descriptor.
     *
     * @return the selected descriptor
     */
    public String getSelectedDescriptor() {
        String selection = (String) descriptorComboBox.getSelectedItem();
        if (selection == null) {
            return null;
        }
        if (selection.equals(LABEL_SELECT)) {
            return null;
        }
        return selection;
    }
    
    /**
     * Get the descriptor table for this chooser
     *
     * @return a Hashtable of descriptors and names
     */
    public Hashtable getDescriptorTable() {
        return descriptorTable;
    }
        
    /**
     * Get any extra key=value pairs that are appended to all requests.
     *
     * @param buff The buffer to append onto
     */
    protected void appendMiscKeyValues(StringBuffer buff) {
        appendKeyValue(buff, PROP_COMPRESS, DEFAULT_COMPRESS);
        appendKeyValue(buff, PROP_PORT, DEFAULT_PORT);
        appendKeyValue(buff, PROP_DEBUG, DEFAULT_DEBUG);
        appendKeyValue(buff, PROP_VERSION, DEFAULT_VERSION);
        appendKeyValue(buff, PROP_USER, getLastAddedUser());
        appendKeyValue(buff, PROP_PROJ, getLastAddedProj());
    }

    public String getLastAddedUser() {
        if (lastServerUser != null && lastServerUser.length() > 0) {
//            System.err.println("appendMisc: using dialog user=" + lastServerUser);
            return lastServerUser;
        }
        else {
//            System.err.println("appendMisc: using default user=" + DEFAULT_USER);
            return DEFAULT_USER;
        }
    }

    public String getLastAddedProj() {
       if (lastServerProj != null && lastServerProj.length() > 0) {
//            System.err.println("appendMisc: using dialog proj=" + lastServerProj);
            return lastServerProj;
        }
        else {
//            System.err.println("appendMisc: using default proj=" + DEFAULT_PROJ);
            return DEFAULT_PROJ;
        }
    }

    /**
     * Show the groups dialog.  This method is not meant to be called
     * but is public by reason of implementation (or insanity).
     */
    public void showGroups() {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new JMenuItem("Reading public datasets..."));
        popup.show(publicButton, 0, (int) publicButton.getBounds().getHeight());

        List groups = readGroups();
    	popup.removeAll();
        if ((groups == null) || (groups.size() == 0)) {
        	popup.add(new JMenuItem("No public datasets available"));
            popup.setVisible(false);
            popup.setVisible(true);
//            popup.repaint();
        	return;
        }
        
        JMenuItem mi;
//        final String[] selected = { null };
        for (int i = 0; i < groups.size(); i++) {
            final String group = groups.get(i).toString();
            mi = new JMenuItem(group);
            mi.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    groupSelector.setSelectedItem(group);
                    doConnect();
                }
            });
            popup.add(mi);
        }
        popup.setVisible(false);
        popup.setVisible(true);
    }

    /**
     * return the String id of the chosen server name
     *
     * @return  the server name
     */
    public String getServer() {
//    	AddeServer server = getAddeServer();
        AddeServer server = getAddeServer2(serverSelector, groupSelector);
    	if (server!=null)
    		return server.getName();
    	else
    		return "";
    }

    protected String getGroup() {
        return getGroup(false);
    }
    
    /**
     * Is the group selector editable?  Override if ya want.
     * @return
     */
    protected boolean isGroupEditable() {
    	return true;
    }
    
    /**
     * Get the image group from the GUI.
     *
     * @return The image group.
     */
    protected String getGroup(final boolean fromGetServer) {
        Object selected = groupSelector.getSelectedItem();
        if (selected == null) {
            return null;
        }
        
        if (selected instanceof AddeServer.Group) {
            AddeServer.Group group = (AddeServer.Group) selected;
            return group.getName();
        }

        if (selected instanceof String) {
            return (String)selected;
        }

        String groupName = selected.toString().trim();
        if (!fromGetServer && (groupName.length() > 0)) {
            //Force the get in case they typed a server name
            getServer();
//            AddeServer server = getAddeServer();
            AddeServer server = getAddeServer2(serverSelector, groupSelector);
            if (server != null) {
                AddeServer.Group group =
                    getIdv().getIdvChooserManager().addAddeServerGroup(
                        server, groupName, getGroupType());
                if (!group.getActive()) {
                    getIdv().getIdvChooserManager().activateAddeServerGroup(
                        server, group);
                }
                //Now put the list of groups back in to the selector
                setGroups();
                groupSelector.setSelectedItem(group);
            }
        }
        return groupName;
    }

    /**
     * Get the server selector
     * @return The server selector
     */
    public JComboBox getServerSelector() {
        if (serverSelector == null)
        	serverSelector = super.getServerSelector();
        
        ItemListener[] ell = serverSelector.getItemListeners();
        for (int i=0; i<ell.length; i++) {
        	serverSelector.removeItemListener((ItemListener)ell[i]);
    	}
    	updateServers();
        updateGroups();
        serverSelector.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if ( !ignoreStateChangedEvents) {
    			    Object selected = serverSelector.getSelectedItem();
    		        if (selected instanceof AddeServer) {
    		            AddeServer selectedServer = (AddeServer)selected;
    		            if (selectedServer != null) {
    		                if (isSeparator(selectedServer)) {
    		                	connectButton.setEnabled(false);
    		                	return;
    		                }
    		            }
    		        }
	            	setState(STATE_UNCONNECTED);
			        connectButton.setEnabled(true);
//                    setGroups();
	            	resetDescriptorBox();
	                updateGroups();
//                    System.err.println("itemStateChanged");
                }
//                else {
//                	System.out.println("Ignoring state change here...");
//                }
            }
        });
                
        serverSelector.getEditor().getEditorComponent().addKeyListener(new KeyListener() {
            public void keyTyped(final KeyEvent e) {}
            public void keyPressed(final KeyEvent e) {}
            public void keyReleased(final KeyEvent e) {
                JTextField field = (JTextField)serverSelector.getEditor().getEditorComponent();
                boolean partialMatch = false;
                for (int i = 0; i < serverSelector.getItemCount(); i++) {
                    String entry = serverSelector.getItemAt(i).toString();
                    if (entry.toLowerCase().startsWith(field.getText().toLowerCase()))
                        partialMatch = true;
                }
                
                if (!partialMatch && groupSelector != null) {
                    ((JTextField)groupSelector.getEditor().getEditorComponent()).setText("");
                }
            }
        });
        
        return serverSelector;
    }
        
    /**
     * Enable or disable the GUI widgets based on what has been
     * selected.
     */
    protected void enableWidgets() {
    	synchronized (WIDGET_MUTEX) {
            boolean newEnabledState = (getState() == STATE_CONNECTED);
            for (int i = 0; i < compsThatNeedDescriptor.size(); i++) {
                JComponent comp = (JComponent) compsThatNeedDescriptor.get(i);
                if (comp.isEnabled() != newEnabledState) {
                    GuiUtils.enableTree(comp, newEnabledState);
                }
            }
    	}
    }
    
    /**
     * Add a listener to the given combobox that will set the
     * state to unconnected
     *
     * @param box The box to listen to.
     */
    protected void clearOnChange(final JComboBox box) {
        box.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if ( !ignoreStateChangedEvents) {
                    setState(STATE_UNCONNECTED);
                    GuiUtils.setListData(descriptorComboBox, new Vector());
//                    System.err.println("clearOnChange");
                }
//                else {
//                	System.out.println("Ignoring state change in clearOnChange for: " + box.toString());
//                }
            }
        });
    }
    
    /**
     * Get the descriptor widget label
     *
     * @return  label for the descriptor  widget
     */
    public String getDescriptorLabel() {
        return "Descriptor";
    }

    protected int getNumTimesToSelect() {
        return 5;
    }
    
    /**
     * Get the default selected index for the relative times list.
     *
     * @return default index
     */
    protected int getDefaultRelativeTimeIndex() {
        return 4;
    }
    
    /**
     * Check the times lists
     */
    protected void checkTimesLists() {
    	super.checkTimesLists();
        if (timesCardPanelExtra == null) {
            return;
        }
        if (getDoAbsoluteTimes()) {
            timesCardPanelExtra.show("absolute");
        } else {
            timesCardPanelExtra.show("relative");
        }
    }
    
    /** Card panel to hold extra relative and absolute time components */
    private GuiUtils.CardLayoutPanel timesCardPanelExtra;
        
    /**
     * Set the relative and absolute extra components
     */
    protected JPanel makeTimesPanel(JComponent relativeCard, JComponent absoluteCard) {
    	JPanel timesPanel = super.makeTimesPanel(false,true);
    	    	
    	// Make a new timesPanel that has extra components tacked on the bottom, inside the tabs
    	Component[] comps = timesPanel.getComponents();
    	
    	if (comps.length==1 && comps[0] instanceof JTabbedPane) {    		
    		timesCardPanelExtra = new GuiUtils.CardLayoutPanel();
    		if (relativeCard == null) relativeCard = new JPanel();
    		if (absoluteCard == null) absoluteCard = new JPanel();
    		timesCardPanelExtra.add(relativeCard, "relative");
    		timesCardPanelExtra.add(absoluteCard, "absolute");
    		timesPanel = GuiUtils.centerBottom(comps[0], timesCardPanelExtra);
    	}
    	
    	return timesPanel;
    }
    
    /**
     * Make the UI for this selector.
     * 
     * Thank you NetBeans for helping with the layout!
     * 
     * @return The gui
     */ 
    private JPanel innerPanel = new JPanel();
    
    private JLabel statusLabel = new JLabel("Status");

    /**
     * Super setStatus() takes a second string to enable "simple" mode
     * which highlights the required component.  We don't really care
     * about that feature, and we don't want getStatusLabel() to
     * change the label background color.
     */
    @Override
    public void setStatus(String statusString, String foo) {
    	if (statusString == null)
    		statusString = "";
    	statusLabel.setText(statusString);
    }
        
    protected void setInnerPanel(JPanel newInnerPanel) {
    	innerPanel = newInnerPanel;
    }
    
    /**
     * Create the basic layout
     */
    protected JComponent doMakeContents() {
    	JPanel outerPanel = new JPanel();
    	
        JLabel serverLabelInner = new JLabel("Server:");  	
        McVGuiUtils.setLabelPosition(serverLabelInner, Position.RIGHT);
        JPanel serverLabel = GuiUtils.leftRight(parameterButton, serverLabelInner);
        McVGuiUtils.setComponentWidth(serverLabel);
        
        clearOnChange(serverSelector);
        McVGuiUtils.setComponentWidth(serverSelector, Width.DOUBLE);

        JLabel groupLabel = McVGuiUtils.makeLabelRight("Dataset:");

        groupSelector.setEditable(isGroupEditable());
        clearOnChange(groupSelector);
        McVGuiUtils.setComponentWidth(groupSelector, Width.DOUBLE);
        
        McVGuiUtils.setComponentWidth(connectButton, Width.DOUBLE);
        connectButton.setActionCommand(CMD_CONNECT);
        connectButton.addActionListener(this);
        
        /** Set the attributes for the descriptor label and combo box, even though
         * they are not used here.  Extending classes can add them to the panel if
         * necessary.
         */
        McVGuiUtils.setComponentWidth(descriptorLabel);
        McVGuiUtils.setLabelPosition(descriptorLabel, Position.RIGHT);
        
    	McVGuiUtils.setComponentWidth(descriptorComboBox, Width.DOUBLEDOUBLE);
        
        if (descriptorComboBox.getMinimumSize().getWidth() < ELEMENT_DOUBLE_WIDTH) {
        	McVGuiUtils.setComponentWidth(descriptorComboBox, Width.DOUBLE);
        }
        
        JLabel statusLabelLabel = McVGuiUtils.makeLabelRight("");
        
        statusLabel.setText("Status");
        McVGuiUtils.setLabelPosition(statusLabel, Position.RIGHT);
        McVGuiUtils.setComponentColor(statusLabel, TextColor.STATUS);

        JButton helpButton = McVGuiUtils.makeImageButton(ICON_HELP, "Show help");
        helpButton.setActionCommand(GuiUtils.CMD_HELP);
        helpButton.addActionListener(this);
        
        JButton refreshButton = McVGuiUtils.makeImageButton(ICON_REFRESH, "Refresh");
        refreshButton.setActionCommand(GuiUtils.CMD_UPDATE);
        refreshButton.addActionListener(this);
                
        McVGuiUtils.setComponentWidth(loadButton, Width.DOUBLE);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(outerPanel);
        outerPanel.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(helpButton)
                        .add(GAP_RELATED)
                        .add(refreshButton)
                        .add(GAP_RELATED)
                        .add(cancelButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(loadButton))
                        .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(innerPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(layout.createSequentialGroup()
                                .add(serverLabel)
                                .add(GAP_RELATED)
                                .add(serverSelector)
                                .add(GAP_RELATED)
                                .add(manageButton)
                                .add(GAP_RELATED)
                                .add(groupLabel)
                                .add(GAP_RELATED)
                                .add(groupSelector)
                                .add(GAP_RELATED)
                                .add(publicButton)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(connectButton))
                            .add(layout.createSequentialGroup()
                                .add(statusLabelLabel)
                                .add(GAP_RELATED)
                                .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
            	.addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(serverLabel)
                    .add(serverSelector)
                    .add(manageButton)
                    .add(groupLabel)
                    .add(groupSelector)
                    .add(publicButton)
                    .add(connectButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(innerPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(statusLabelLabel)
                    .add(statusLabel))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(loadButton)
                    .add(cancelButton)
                    .add(refreshButton)
                    .add(helpButton))
                .addContainerGap())
        );
    
        return outerPanel;

    }
    
    public class ServerComparator implements Comparator<AddeServer> {
    	public int compare(AddeServer server1, AddeServer server2) {
    		return server1.getName().compareTo(server2.getName());
    	}
    }
    
    public class GroupComparator implements Comparator<Group> {
    	public int compare(Group group1, Group group2) {
    		return group1.getName().compareTo(group2.getName());
    	}
    }
    
}

