package edu.wisc.ssec.mcidasv.startupmanager.options;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import edu.wisc.ssec.mcidasv.startupmanager.options.OptionMaster.OptionPlatform;
import edu.wisc.ssec.mcidasv.startupmanager.options.OptionMaster.Type;
import edu.wisc.ssec.mcidasv.startupmanager.options.OptionMaster.Visibility;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils;

/**
 * Representation of a choice allowing the user to select the global McIDAS-V
 * logging level.
 */
public class LoggerLevelOption extends AbstractOption {
    
    /** 
     * {@code String} representation of Logback's {@literal "TRACE"} logging 
     * level. 
     */
    private static final String TRACE = "TRACE";
    
    /** 
     * {@code String} representation of Logback's {@literal "DEBUG"} logging 
     * level. 
     */
    private static final String DEBUG = "DEBUG";
    
    /** 
     * {@code String} representation of Logback's {@literal "INFO"} logging 
     * level. 
     */
    private static final String INFO = "INFO";
    
    /** 
     * {@code String} representation of Logback's {@literal "WARN"} logging 
     * level. 
     */
    private static final String WARN = "WARN";
    
    /** 
     * {@code String} representation of Logback's {@literal "ERROR"} logging 
     * level. 
     */
    private static final String ERROR = "ERROR";
    
    /** 
     * {@code String} representation of Logback's {@literal "OFF"} logging 
     * level. 
     */
    private static final String OFF = "OFF";
    
    /** 
     * {@code JComboBox} that will eventually contain logging levels to 
     * select. May be {@code null}. 
     */
    private JComboBox comboBox;
    
    /** 
     * {@code String} representation of the user's selection, or the default
     * value provided to the constructor. 
     */
    private String currentChoice;
    
    /**
     * Create a startup option that allows the user to manipulate the global
     * McIDAS-V logging level. <B>NOTE:</b> {@code null} is not a permitted 
     * value for any of this constructor's parameters.
     * 
     * @param id Identifier for this startup option.
     * @param label Brief description suitable for a GUI label.
     * @param defaultValue Default value for this startup option.
     * @param optionPlatform Platforms where this option may be applied.
     * @param optionVisibility Whether or not the option is presented via the GUI.
     * 
     * @throws IllegalArgumentException if {@code defaultValue} failed {@link #isValidValue(String)}.
     */
    public LoggerLevelOption(String id, String label, String defaultValue,
            OptionPlatform optionPlatform, Visibility optionVisibility) {
        super(id, label, Type.LOGLEVEL, optionPlatform, optionVisibility);
        if (!isValidValue(defaultValue)) {
            throw new IllegalArgumentException("Default value '"+defaultValue+"' is not one of: TRACE, DEBUG, INFO, WARN, ERROR, or OFF.");
        }
        currentChoice = defaultValue;
    }
    
    /**
     * Builds a {@link JComboBox} containing the logging levels to select. Defaults to the {@code String} specified 
     * in the constructor.
     * 
     * @return {@code JComboBox} to present to the user.
     */
    public JComboBox getComponent() {
        comboBox = new JComboBox(new String[] { TRACE, DEBUG, INFO, WARN, ERROR, OFF });
        comboBox.setSelectedItem(currentChoice);
        comboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                setValue(comboBox.getSelectedItem().toString());
            }
        });
        
        McVGuiUtils.setComponentWidth(comboBox, McVGuiUtils.Width.ONEHALF);
        return comboBox;
    }
    
    /**
     * Returns the user's current selection (or the default value).
     * 
     * @return Current selection or default value.
     */
    public String getValue() {
        return currentChoice;
    }
    
    /**
     * Stores the user's selected logging level. Note that this can be called from third-party or the GUI! If the call
     * originates from the GUI, an infinite loop is avoided by using the {@link JComboBox#setSelectedItem(Object)} 
     * behavior that does <b>not</b> generate {@link ItemEvent ItemEvents} if the selection did not actually change.
     * 
     * @param value {@code String} representation of the desired logging 
     * level. Should not be {@code null}.
     * 
     * @throws IllegalArgumentException if {@code value} failed {@link #isValidValue(String)}.
     */
    public void setValue(String value) {
        if (!isValidValue(value)) {
            throw new IllegalArgumentException("Value '"+value+"' is not one of: TRACE, DEBUG, INFO, WARN, ERROR, or OFF.");
        }
        currentChoice = value;
        Logger rootLogger = (Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(stringToLogback(value));
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (comboBox != null) {
                    comboBox.setSelectedItem(currentChoice);
                }
            }
        });
        System.out.println("this: "+this);
    }
    
    /**
     * Converts a {@code String} value to the corresponding logging level.
     * 
     * @param value Value to convert.
     * 
     * @return Logging level.
     * 
     * @throws IllegalArgumentException if {@code value} did not have a 
     * corresponding logging level.
     */
    private static Level stringToLogback(final String value) {
        Level level;
        if (TRACE.equalsIgnoreCase(value)) {
            level = Level.TRACE;
        } else if (DEBUG.equalsIgnoreCase(value)) {
            level = Level.DEBUG;
        } else if (INFO.equalsIgnoreCase(value)) {
            level = Level.INFO;
        } else if (WARN.equalsIgnoreCase(value)) {
            level = Level.WARN;
        } else if (ERROR.equalsIgnoreCase(value)) {
            level = Level.ERROR;
        } else if (OFF.equalsIgnoreCase(value)) {
            level = Level.OFF;
        } else {
            throw new IllegalArgumentException();
        }
        return level;
    }
    
    /**
     * Tests a {@code String} value to see if it has a corresponding logging
     * level.
     * 
     * @param value Value to test.
     * 
     * @return {@code true} if-and-only-if passes a 
     * {@link String#equalsIgnoreCase(String)} check against {@link #TRACE}, 
     * {@link #DEBUG}, {@link #INFO}, {@link #WARN}, {@link #ERROR}, or 
     * {@link #OFF}.
     */
    private static boolean isValidValue(final String value) {
        return (TRACE.equalsIgnoreCase(value) || 
                DEBUG.equalsIgnoreCase(value) || 
                INFO.equalsIgnoreCase(value) || 
                WARN.equalsIgnoreCase(value) || 
                ERROR.equalsIgnoreCase(value) || 
                OFF.equalsIgnoreCase(value));
    }
    
    /**
     * {@code String} representation of the user's logging level selection.
     * 
     * @return {@code String} that looks something like 
     * {@literal "[LoggerLevel@7825114a: currentChoice=INFO]"}.
     */
    public String toString() {
        return String.format("[LoggerLevelOption@%x: currentChoice=%s]",
                hashCode(), currentChoice);
    }
}
