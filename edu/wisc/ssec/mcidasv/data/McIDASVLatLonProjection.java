package edu.wisc.ssec.mcidasv.data;

import ucar.unidata.geoloc.ProjectionImpl;
import ucar.unidata.geoloc.ProjectionRect;
import ucar.unidata.geoloc.projection.LatLonProjection;

/**
 * An extension of <tt>LatLonProjection</tt> that adds properties for 
 * the center point and dimensions of the projection box.  Appropriate
 * getters and setters are added so they will be picked up by the
 * <tt>ProjectionImpl</tt> and thereby editable by the user.
 * 
 * @author McIDAS-V Dev Team, UW SSEC
 * @version $Id$
 */
public class McIDASVLatLonProjection extends LatLonProjection {

	private static final long serialVersionUID = -4939597425144220140L;

	public McIDASVLatLonProjection() {
		this("");
	}

	public McIDASVLatLonProjection(String name) {
		this(name, new ProjectionRect(-180, -90, 180, 90));
	}

	public McIDASVLatLonProjection(String name, ProjectionRect mapArea) {
        addParameter(ATTR_NAME, "McVLatLon");
        this.name = name;
        isLatLon = true;
        defaultMapArea = mapArea;
	}
	
    /**
     * Get the class name
     * @return class name
     */
    public String getClassName() {
        return "McVLatLon";
    }


    /**
     * Get the label to be used in the gui for this type of projection
     *
     * @return Type label
     */
    public String getProjectionTypeLabel() {
        return "McV Lat/Lon";
    }
	
	/**
	 * Set the center of the projection box X coord.
	 * @param x
	 */
	public void setCenterX(double x) {
		defaultMapArea.x = x - defaultMapArea.width/2;
	}
	
	/**
	 * Set the center of the projection box Y coord.
	 * @param y
	 */
	public void setCenterY(double y) {
		defaultMapArea.y = y - defaultMapArea.height/2;
	}
	
	/**
	 * Set the overall width of the projection box.
	 * @param w
	 */
	public void setLonWidth(double w) {
		defaultMapArea.width = w;
	}
	
	/**
	 * Set the overall height of the projection box
	 * @param h
	 */
	public void setLatHeight(double h) {
		defaultMapArea.height = h;
	}
	
	public double getCenterX() {
		return defaultMapArea.x + defaultMapArea.width/2;
	}
	
	public double getCenterY() {
		return defaultMapArea.y + defaultMapArea.height/2;
	}

	public double getLonWidth() {
		return defaultMapArea.width;
	}
	
	public double getLatHeight() {
		return defaultMapArea.height;
	}
	
    /**
     * Make the default display projection
     * @return Default display projection
     */
    protected ProjectionImpl makeDefaultProjection() {
        return new McIDASVLatLonProjection("World",
                                    new ProjectionRect(-180., -180., 180.,
                                        180.));
    }
	
}
