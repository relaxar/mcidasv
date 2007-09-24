package edu.wisc.ssec.mcidasv.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * A popup window that attaches itself to a parent and can display an 
 * component without preventing user interaction like a <tt>JComboBox</tt>.
 *   
 * @author <a href="http://www.ssec.wisc.edu/cgi-bin/email_form.cgi?name=Flynn,%20Bruce">Bruce Flynn, SSEC</a>
 *
 */
/**
 * @author <a href="http://www.ssec.wisc.edu/cgi-bin/email_form.cgi?name=Flynn,%20Bruce">Bruce Flynn, SSEC</a>
 *
 */
public class ComponentPopup extends JWindow {

	private static final long serialVersionUID = 7394231585407030118L;
	
	/**
	 * Number of pixels to use to compenstate for when the mouse is moved slowly 
	 * thereby hiding this popup when between components.
	 */
	private static final int FLUFF = 3;

	/**
	 * Get the calculated total screen size.
	 * @return
	 */
	protected static Dimension getScreenSize() {
		GraphicsEnvironment genv = GraphicsEnvironment.getLocalGraphicsEnvironment();
	    GraphicsDevice gdev = genv.getDefaultScreenDevice();
	    DisplayMode dmode = gdev.getDisplayMode();

	    return new Dimension(dmode.getWidth(), dmode.getHeight());
	}
	
	/**
	 * Do we contain the screen relative point.
	 * @param point Screen relative point.
	 * @return
	 */
	public boolean containsPoint(Component comp, Point point) {
		if (!comp.isVisible()) {
			return false;
		}
		Point my = comp.getLocationOnScreen();
		boolean containsX = point.x > my.x - FLUFF && point.x < my.x + getWidth() + FLUFF;
		boolean containsY = point.y > my.y - FLUFF && point.y < my.y + getHeight() + FLUFF;
		return containsX && containsY;
	}	
	
	/**
	 * Determines if the mouse is on me.
	 */
	private final MouseAdapter ourHideAdapter;
	/**
	 * Determines if the mouse is on my dad.
	 */
	private final MouseAdapter parentsHideAdapter;
	private Component parent;
	
	/**
	 * Create an instance associated with the given parent.
	 * @param parent The component to attach this instance to.
	 */
	public ComponentPopup(Component parent) {
		ourHideAdapter = new MouseAdapter() {
			public void mouseExited(MouseEvent evt) {
				PointerInfo info = MouseInfo.getPointerInfo();
				boolean onParent = containsPoint(
					ComponentPopup.this.parent,
					info.getLocation()
				);
				
				if (isVisible() && !onParent) {
					setVisible(false);
				}
			}
		};
		parentsHideAdapter = new MouseAdapter() {
			public void mouseExited(MouseEvent evt) {
				PointerInfo info = MouseInfo.getPointerInfo();
				boolean onComponent = containsPoint(
					ComponentPopup.this,
					info.getLocation()
				);
				if (isVisible() && !onComponent) {
					setVisible(false);
				}
			}
		};
		setParent(parent);
	}
	
	/**
	 * Set our parent. If there is currently a parent remove the associated
	 * listeners and add them to the new parent.
	 * @param comp
	 */
	public void setParent(Component comp) {
		if (parent != null) {
			parent.removeMouseListener(parentsHideAdapter);
		}
		
		parent = comp;
		parent.addMouseListener(parentsHideAdapter);
	}
	
	/**
	 * Show this popup above the parent. It is not checked if
	 * the component will fit on the screen.
	 */
	public void showAbove() {
		Point loc = parent.getLocationOnScreen();
		int x = loc.x;
		int y = loc.y - getHeight();
		showPopupAt(x, y);
	}
	
	/**
	 * Show this popup below the parent. It is not checked if
	 * the component will fit on the screen.
	 */
	public void showBelow() {
		Point loc = parent.getLocationOnScreen();
		int x = loc.x;
		int y = loc.y + parent.getHeight();
		showPopupAt(x, y);
	}
	
	/**
	 * Do we fit between the top of the parent and the top edge
	 * of the screen.
	 * @return
	 */
	protected boolean fitsAbove() {
		Point loc = parent.getLocationOnScreen();
		int myH = getHeight();
		return loc.y - myH > 0;
	}
	
	/**
	 * Do we fit between the bottom of the parent and the edge
	 * of the screen.
	 * @return
	 */
	protected boolean fitsBelow() {
		Point loc = parent.getLocationOnScreen();
		Dimension scr = getScreenSize();
		int myH = getHeight();
		return loc.y + parent.getHeight() + myH < scr.height;
	}
	
	/**
	 * Show at the specified X and Y.
	 * @param x
	 * @param y
	 */
	public void showPopupAt(int x, int y) {
		setLocation(x, y);
		setVisible(true);
	}
	
	/**
	 * Show this popup deciding whether to show it above
	 * or below the parent component.
	 */
	public void showPopup() {
		if (fitsBelow()) {
			showBelow();
		} else {
			showAbove();
		}
	}

	/**
	 * Overridden to make sure our hide listeners are added to child components.
	 * @see javax.swing.JWindow#addImpl(java.awt.Component, java.lang.Object, int)
	 */
	protected void addImpl(Component comp, Object constraints, int index) {
		super.addImpl(comp, constraints, index);
		comp.addMouseListener(ourHideAdapter);
	}
	
	/**
	 * Test method.
	 */
	private static void createAndShowGui() {
		
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree tree = new JTree(model);
        tree.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        
        root.add(new DefaultMutableTreeNode("Child 1"));
        root.add(new DefaultMutableTreeNode("Child 2"));
        root.add(new DefaultMutableTreeNode("Child 3"));
        
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandPath(tree.getPathForRow(i));
        }
        
        final JButton button = new JButton("Popup");
        final ComponentPopup cp = new ComponentPopup(button);
        cp.add(tree, BorderLayout.CENTER);
        cp.pack();
        
        button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				cp.showPopup();
			}
        });
        
        JFrame frame = new JFrame("ComponentPopup");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());
        frame.add(button);
        frame.pack();
        frame.setVisible(true);
	}
	
	/**
	 * Test method.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			javax.swing.UIManager.setLookAndFeel(
					javax.swing.UIManager.getCrossPlatformLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGui();
			}
		});
	}
	
}
