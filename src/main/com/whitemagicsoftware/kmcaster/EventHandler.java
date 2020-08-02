/*
 * Copyright 2020 White Magic Software, Ltd.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.whitemagicsoftware.kmcaster;

import com.whitemagicsoftware.kmcaster.ui.AutofitLabel;
import com.whitemagicsoftware.kmcaster.ui.ResetTimer;
import com.whitemagicsoftware.kmcaster.util.ConsecutiveEventCounter;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static com.whitemagicsoftware.kmcaster.HardwareState.SWITCH_PRESSED;
import static com.whitemagicsoftware.kmcaster.HardwareState.SWITCH_RELEASED;
import static com.whitemagicsoftware.kmcaster.HardwareSwitch.KEY_REGULAR;
import static com.whitemagicsoftware.kmcaster.HardwareSwitch.MOUSE_UNDEFINED;
import static com.whitemagicsoftware.kmcaster.LabelConfig.*;
import static com.whitemagicsoftware.kmcaster.ui.Constants.*;
import static java.awt.Toolkit.getDefaultToolkit;
import static javax.swing.RepaintManager.currentManager;
import static javax.swing.SwingUtilities.invokeLater;

/**
 * Responsible for controlling the application state between the events
 * and the view.
 */
public final class EventHandler implements PropertyChangeListener {

  /**
   * Maps key pressed states to key cap title colours.
   */
  private static final Map<HardwareState, Color> KEY_COLOURS = Map.of(
      SWITCH_PRESSED, COLOUR_KEY_DN,
      SWITCH_RELEASED, COLOUR_KEY_UP
  );

  /**
   * This is used to temporarily set the mouse to the released state.
   */
  private static final HardwareSwitchState MOUSE_RELEASED =
      new HardwareSwitchState( MOUSE_UNDEFINED, SWITCH_RELEASED );

  private final HardwareImages mHardwareImages;
  private final AutofitLabel[] mLabels = new AutofitLabel[ LabelConfig.size() ];
  private final Map<HardwareSwitch, ResetTimer> mTimers = new HashMap<>();

  public EventHandler(
      final HardwareImages hardwareImages, final UserSettings userSettings ) {
    mHardwareImages = hardwareImages;

    final var keyColour = KEY_COLOURS.get( SWITCH_PRESSED );

    for( final var config : LabelConfig.values() ) {
      final var label = new AutofitLabel( config.toTitleCase(), LABEL_FONT );

      label.setVerticalAlignment( config.getVerticalAlign() );
      label.setHorizontalAlignment( config.getHorizontalAlign() );
      label.setForeground( keyColour );

      mLabels[ config.ordinal() ] = label;

      config.getHardwareSwitch().ifPresentOrElse(
          s -> mHardwareImages.get( s ).add( label ),
          () -> mHardwareImages.get( KEY_REGULAR ).add( label )
      );
    }

    final var delayModifier = userSettings.getDelayKeyModifier();
    final var delayRegular = userSettings.getDelayKeyRegular();
    final var delayButton = userSettings.getDelayMouseButton();

    for( final var key : HardwareSwitch.keyboardSwitches() ) {
      final var delay = key.isModifier() ? delayModifier : delayRegular;

      mTimers.put( key, new ResetTimer( delay ) );
    }

    for( final var key : HardwareSwitch.mouseSwitches() ) {
      mTimers.put( key, new ResetTimer( delayButton ) );
    }
  }

  /**
   * Called when a hardware switch has changed state.
   *
   * @param e Contains the identifier for the switch, its previous value,
   *          and its new value.
   */
  @Override
  public void propertyChange( final PropertyChangeEvent e ) {
    invokeLater(
        () -> {
          update( e );

          // Prevent collapsing multiple paint events.
          getDefaultToolkit().sync();
        }
    );
  }

  private final Deque<HardwareSwitch> mMousePressed = new LinkedList<>();

  /**
   * Called to update the user interface after a keyboard or mouse event
   * has fired. This must be invoked from Swing's event dispatch thread.
   *
   * @param e Contains the identifier for the switch, its previous value,
   *          and its new value.
   */
  private void update( final PropertyChangeEvent e ) {
    final var switchName = e.getPropertyName();
    final var oldValue = e.getOldValue().toString();
    final var newValue = e.getNewValue().toString();
    final var switchValue = newValue.isEmpty() ? oldValue : newValue;

    final var hwSwitch = HardwareSwitch.valueFrom( switchName );
    final var hwState = HardwareState.valueFrom( newValue );

    final var switchState = new HardwareSwitchState(
        hwSwitch, hwState, switchValue );

    // Get the mouse timer, modifier key timer, or non-modifier key timer.
    final var timer = getTimer( hwSwitch );

    if( hwSwitch.isKeyboard() ) {
      if( hwState == SWITCH_RELEASED ) {
        timer.addActionListener(
            ( event ) -> updateKeyboardLabel( switchState )
        );
      }
      else {
        timer.stop();
        updateKeyboardLabel( switchState );
      }
    }
    else {
      if( hwState == SWITCH_RELEASED ) {
        mMousePressed.remove( hwSwitch );
        timer.addActionListener(
            ( event ) -> updateMouseStatus( switchState )
        );
      }
      else {
        timer.stop();
        mMousePressed.add( hwSwitch );
        updateMouseStatus( switchState );
      }
    }
  }

  protected void updateSwitchState( final HardwareSwitchState switchState ) {
    getHardwareComponent( switchState ).setState( switchState );
  }

  private final ConsecutiveEventCounter<String> mKeyCounter =
      new ConsecutiveEventCounter<>( 9 );

  /**
   * Changes the text on labels when the state of a key changes.
   *
   * @param state The key that has changed.
   */
  protected synchronized void updateKeyboardLabel(
      final HardwareSwitchState state ) {
    updateSwitchState( state );
    final var hwState = state.getHardwareState();

    if( state.isModifier() ) {
      updateLabel( state );

      mKeyCounter.reset();
    }
    else {
      // Hide any previously displayed labels.
      getLabel( LABEL_REGULAR ).setVisible( false );

      final var main = getLabel( LABEL_REGULAR_NUM_MAIN );
      final var sup = getLabel( LABEL_REGULAR_NUM_SUPERSCRIPT );
      final var tally = getLabel( LABEL_REGULAR_COUNTER );

      main.setVisible( false );
      sup.setVisible( false );
      tally.setVisible( false );

      if( hwState == SWITCH_PRESSED ) {
        final var keyValue = state.getValue();

        // Determine whether there are separate parts for the key label.
        final var index = keyValue.indexOf( ' ' );

        // If there's a space in the name, the text before the space is
        // positioned in the upper-left while the text afterwards takes up
        // the remainder. This is used for number pad keys, backspace, enter,
        // tab, and a few others.
        if( index > 0 ) {
          // Label for "Num", "Back", "Tab", and other dual-labelled keys.
          sup.setText( keyValue.substring( 0, index ) );
          sup.transform( .6f );

          // Label for number pad keys or icon glyphs.
          main.setText( keyValue.substring( index + 1 ) );
          main.transform( .8f );

          // Shift the main label down away from the superscript.
          final var mainLoc = main.getLocation();
          main.setLocation( mainLoc.x, mainLoc.y + (sup.getHeight() / 2) );

          main.setVisible( true );
          sup.setVisible( true );
        }
        else {
          updateLabel( state );
        }

        // Track the consecutive key presses for this value.
        if( mKeyCounter.apply( keyValue ) ) {
          tally.setText( mKeyCounter.toString() );
          tally.transform( .25f );
          tally.setVisible( true );
        }
      }
    }
  }

  private void updateMouseStatus( final HardwareSwitchState switchState ) {
    final var container = getHardwareComponent( MOUSE_RELEASED );
    final var rm = currentManager( container );
    final var button = getLabel( LABEL_MOUSE_UNDEFINED );
    final var hwSwitch = switchState.getHardwareSwitch();
    final var hwState = switchState.getHardwareState();

    if( hwSwitch == MOUSE_UNDEFINED ) {
      if( hwState == SWITCH_PRESSED ) {
        button.setText( switchState.getValue() );
        button.transform();
        button.setVisible( true );
      }
      else {
        button.setVisible( false );
      }
    }

    container.setState( new HardwareSwitchState( hwSwitch, SWITCH_RELEASED ) );
    rm.paintDirtyRegions();

    for( final var mouseSwitch : mMousePressed ) {
      final var buttonState = new HardwareSwitchState(
          mouseSwitch, SWITCH_PRESSED );

      container.setState( buttonState );
      rm.paintDirtyRegions();
    }
  }

  /**
   * Changes the text label and colour for the given state.
   *
   * @param state The state of the hardware switch to look up.
   */
  private void updateLabel( final HardwareSwitchState state ) {
    final var container = getHardwareComponent( state );
    final var label = (AutofitLabel) container.getComponent( 0 );

    label.setVisible( false );
    label.setForeground( KEY_COLOURS.get( state.getHardwareState() ) );
    label.setText( state.getValue() );
    label.transform();
    label.setVisible( true );
  }

  private HardwareComponent<HardwareSwitchState, Image> getHardwareComponent(
      final HardwareSwitchState state ) {
    return mHardwareImages.get( state.getHardwareSwitch() );
  }

  private AutofitLabel getLabel( final LabelConfig config ) {
    return mLabels[ config.ordinal() ];
  }

  private ResetTimer getTimer( final HardwareSwitch hwSwitch ) {
    return mTimers.get( hwSwitch );
  }
}
