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
package com.whitemagicsoftware.kmcaster.listeners;

import org.jnativehook.keyboard.NativeKeyEvent;

import javax.swing.event.ChangeListener;

/**
 * Responsible for tracking the state of modifier keys.
 */
class Modifier {
  private boolean mHeld;
  private final int mMask;
  private final String mName;

  Modifier( final String name, final int mask ) {
    assert name != null;

    mName = name.toLowerCase();
    mMask = mask;
  }

  public boolean isName( final String name ) {
    return mName.equals( name );
  }

  public boolean isHeld() {
    return mHeld;
  }

  public void setHeld( final boolean held ) {
    mHeld = held;
  }

  /**
   * Answers whether the given {@link NativeKeyEvent} modifiers are set to
   * include the modifiers defined by this instance.
   *
   * @param e The event that has modifiers to check.
   * @return {@code true} This modifier matches the given event's modifiers.
   */
  public boolean matches( final NativeKeyEvent e ) {
    return (e.getModifiers() & mMask) != 0;
  }

  /**
   * Returns a unique identifier for this modifier that can be used by
   * a {@link ChangeListener}.
   *
   * @return The modifier key name, in lowercase.
   */
  @Override
  public String toString() {
    return mName;
  }
}
