use enigo::{Button, Direction, Mouse, Axis, Coordinate};


/// Tracks the current state of mouse buttons.
///
/// This struct maintains the state (pressed/released) of the three standard mouse buttons:
/// - Left button
/// - Middle button (scroll wheel)
/// - Right button
///
/// The state is used to prevent duplicate press/release actions and ensure proper button state
/// synchronization between client and server.
///
/// # Fields
///
/// * `left` - State of the left mouse button (true = pressed, false = released)
/// * `middle` - State of the middle mouse button (true = pressed, false = released)
/// * `right` - State of the right mouse button (true = pressed, false = released)
#[derive(Default)]
struct ButtonPressed {
    left: bool,
    middle: bool,
    right: bool,
}

pub struct MouseControl {
    button_pressed: ButtonPressed,
}

impl MouseControl {
    pub fn new() -> Self {
        MouseControl {
            button_pressed: ButtonPressed::default(),
        }
    }

    /// Calculates the adjusted movement to keep the new location within the valid range [0, max].
    ///
    /// # Arguments
    ///
    /// * `mov` - The proposed movement.
    /// * `loc` - The current location.
    /// * `max` - The maximum allowed location.
    ///
    /// # Returns
    ///
    /// The adjusted movement.
    ///
    /// # Details
    ///
    /// The function ensures that the new location (`loc + mov`) stays within the valid range [0, `max`].
    /// If the new location is less than or equal to 0, the movement is adjusted to `-loc`.
    /// If the new location is greater than or equal to `max`, the movement is adjusted to `max - loc`.
    /// Otherwise, the movement is kept as is.
    fn adjust_move(&self, mov: i32, loc: i32, max: i32) -> i32 {
        std::cmp::min(
            std::cmp::max(mov, -loc), 
            max - loc
        )
    }

    /// Processes mouse control packets and executes corresponding mouse actions.
    /// 
    /// # Arguments
    /// 
    /// * `dat` - Three-byte array containing the mouse control packet:
    ///   - Byte 1: Header (4 bits) | Data (4 bits)
    ///   - Byte 2: Data
    ///   - Byte 3: Data
    /// * `mouse` - Implementation of the Mouse trait for executing mouse actions
    /// 
    /// # Protocol
    /// 
    /// - `0x0`: Mouse movement
    ///   - 10-bit signed X movement in lower bits
    ///   - 10-bit signed Y movement in middle bits
    /// - `0x1`: Scroll
    ///   - 10-bit signed horizontal scroll
    ///   - 10-bit signed vertical scroll
    /// - `0x2`: Button actions
    ///   - `0x0/0x1`: Left button press/release
    ///   - `0x2/0x3`: Middle button press/release
    ///   - `0x4/0x5`: Right button press/release
    /// 
    pub fn mouse_action(&mut self, dat: [u8; 3], mouse: &mut impl Mouse) {
        let data = ((dat[0] as u32) << 16) | ((dat[1] as u32) << 8) | (dat[2] as u32);
        //println!("{:06x}", data);

        let header = (data & 0xF00000) >> 20;
        match header {
            0x0 => {
                // move cursor
                let payload_x = (((data & 0x03FF) as i32) << 22) >> 22;
                let payload_y = (((data & (0x03FF << 10)) as i32) << 12) >> 22;

                let loc = mouse.location().unwrap();
                let display_size = mouse.main_display().unwrap();
                let x = self.adjust_move(payload_x, loc.0, display_size.0);
                let y = self.adjust_move(payload_y, loc.1, display_size.1);
                mouse.move_mouse(x, y, Coordinate::Rel).expect("Unable to move mouse");
            },
            0x1 => { 
                // scroll page
                let payload_x = (((data & 0x03FF) as i32) << 22) >> 22;
                let payload_y = (((data & (0x03FF << 10)) as i32) << 12) >> 22;

                // horizontal scroll
                mouse.scroll(payload_x, Axis::Horizontal).expect("Unable to scroll horizontally");
                // vertical scroll
                mouse.scroll(payload_y, Axis::Vertical).expect("Unable to scroll vertically");
            },
            0x2 => {
                let payload = data & 0x0FFFFF;
                match payload {
                    0x0  => {
                        if self.button_pressed.left == false {
                            mouse.button(Button::Left, Direction::Press).expect("Unable to press left mouse button");
                            self.button_pressed.left = true;
                        }
                    }, 
                    0x1 => {
                        if self.button_pressed.left == true {
                            mouse.button(Button::Left, Direction::Release).expect("Unable to release left mouse button");
                            self.button_pressed.left = false;
                        }
                    },
                    0x2 => {
                        if self.button_pressed.middle == false {
                            mouse.button(Button::Middle, Direction::Press).expect("Unable to press middle mouse button");
                            self.button_pressed.middle = true;
                        }
                    },
                    0x3 => {
                        if self.button_pressed.middle == true {
                            mouse.button(Button::Middle, Direction::Release).expect("Unable to release middle mouse button");
                            self.button_pressed.middle = false;
                        }
                    },
                    0x4 => {
                        if self.button_pressed.right == false {
                            mouse.button(Button::Right, Direction::Press).expect("Unable to press right mouse button");
                            self.button_pressed.right = true;
                        }
                    },
                    0x5 => {
                        if self.button_pressed.right == true {
                            mouse.button(Button::Right, Direction::Release).expect("Unable to release right mouse button");
                            self.button_pressed.right = false;
                        }
                    },
                    _ => {
                        // ignore
                        println!("Ignored: {:x}", payload);
                    }
                }
            },
            _ => {
                // ignore
                println!("Ignored header: {:02x}", header);
            }
        }
    }
}



// Unit tests

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Default)]
    struct MockMouse {
        location: (i32, i32),
        display_size: (i32, i32),
        button_pressed: ButtonPressed,
    }

    impl Mouse for MockMouse {
        fn move_mouse(&mut self, x: i32, y: i32, _: Coordinate) -> enigo::InputResult<()> {
            self.location.0 += x;
            self.location.1 += y;
            Ok(())
        }

        fn scroll(&mut self, length: i32, axis: Axis) -> enigo::InputResult<()> {
            if axis == Axis::Horizontal {
                self.location.0 += length;
            } else {
                self.location.1 += length;
            }
            Ok(())
        }

        fn button(&mut self, button: Button, direction: Direction) -> enigo::InputResult<()> {
            if button == Button::Left {
                self.button_pressed.left = direction == Direction::Press;
            } else if button == Button::Middle {
                self.button_pressed.middle = direction == Direction::Press;
            } else if button == Button::Right {
                self.button_pressed.right = direction == Direction::Press;
            }
            Ok(())
        }

        fn location(&self) -> enigo::InputResult<(i32, i32)> {
            Ok(self.location)
        }

        fn main_display(&self) -> enigo::InputResult<(i32, i32)> {
            Ok(self.display_size)
        }
    }

    #[test]
    fn adjust_move_test() {
        let mc = MouseControl::new();
        
        assert_eq!(mc.adjust_move(3, 10, 100), 3);
        assert_eq!(mc.adjust_move(0, 10, 100), 0);
        assert_eq!(mc.adjust_move(-3, 10, 100), -3);
        assert_eq!(mc.adjust_move(-10, 10, 100), -10);
        assert_eq!(mc.adjust_move(-11, 10, 100), -10);
        assert_eq!(mc.adjust_move(90, 10, 100), 90);
        assert_eq!(mc.adjust_move(91, 10, 100), 90);
        assert_eq!(mc.adjust_move(0, 100, 100), 0);
        assert_eq!(mc.adjust_move(1, 100, 100), 0);
        assert_eq!(mc.adjust_move(-1, 100, 100), -1);
        assert_eq!(mc.adjust_move(1, 0, 100), 1);
        assert_eq!(mc.adjust_move(-1, 0, 100), 0); 
        assert_eq!(mc.adjust_move(0, 0, 100), 0);
    }

    #[test]
    fn mouse_move_test() {
        let mut mouse = MockMouse {
            location: (10, 10),
            display_size: (100, 100),
            button_pressed: ButtonPressed::default(),
        };
        let mut mc = MouseControl::new();

        // x += 0, y += 0
        mc.mouse_action([0x00, 0x00, 0x00], &mut mouse);
        assert_eq!(mouse.location, (10, 10));

        // x += 1
        mc.mouse_action([0x00, 0x00, 0x01], &mut mouse);
        assert_eq!(mouse.location, (11, 10));

        // y += 1
        mc.mouse_action([0x00, 0x04, 0x00], &mut mouse);
        assert_eq!(mouse.location, (11, 11));

        // x += 1, y += 1
        mc.mouse_action([0x00, 0x04, 0x01], &mut mouse);
        assert_eq!(mouse.location, (12, 12));
        
        let d: i32 = -1;
        // x -= 1
        mc.mouse_action([0x00, ((d >> 8) & 0x03) as u8, (d & 0xFF) as u8], &mut mouse);
        assert_eq!(mouse.location, (11, 12));
        
        // y -= 1
        mc.mouse_action([((d >> 6) & 0x0F) as u8, ((d << 2) & 0xFC) as u8, 0x00], &mut mouse);
        assert_eq!(mouse.location, (11, 11));

        // x -= 1, y -= 1
        mc.mouse_action([((d >> 6) & 0x0F) as u8, (((d << 2) & 0xFC) | ((d >> 8) & 0x03)) as u8, (d & 0xFF) as u8], &mut mouse);
        assert_eq!(mouse.location, (10, 10));
    }


    #[test]
    fn mouse_scroll_test() {
        let mut mouse = MockMouse {
            location: (10, 10),
            display_size: (100, 100),
            button_pressed: ButtonPressed::default(),
        };
        let mut mc = MouseControl::new();

        // x += 0, y += 0
        mc.mouse_action([0x10, 0x00, 0x00], &mut mouse);
        assert_eq!(mouse.location, (10, 10));

        // x += 2
        mc.mouse_action([0x10, 0x00, 0x02], &mut mouse);
        assert_eq!(mouse.location, (12, 10));

        // y += 2
        mc.mouse_action([0x10, 0x08, 0x00], &mut mouse);
        assert_eq!(mouse.location, (12, 12));

        // x += 2, y += 2
        mc.mouse_action([0x10, 0x08, 0x02], &mut mouse);
        assert_eq!(mouse.location, (14, 14));

        let d: i32 = -2;
        // x -= 2
        mc.mouse_action([0x10, ((d >> 8) & 0x03) as u8, (d & 0xFF) as u8], &mut mouse);
        assert_eq!(mouse.location, (12, 14));

        // y -= 2
        mc.mouse_action([0x10 | ((d >> 6) & 0x0F) as u8, ((d << 2) & 0xFC) as u8, 0x00], &mut mouse);
        assert_eq!(mouse.location, (12, 12));

        // x -= 2, y -= 2
        mc.mouse_action([((d >> 6) & 0x0F) as u8, (((d << 2) & 0xFC) | ((d >> 8) & 0x03)) as u8, (d & 0xFF) as u8], &mut mouse);
        assert_eq!(mouse.location, (10, 10));
    }

    #[test]
    fn mouse_button_test() {
        let mut mouse = MockMouse {
            location: (10, 10),
            display_size: (100, 100),
            button_pressed: ButtonPressed::default(),
        };
        let mut mc = MouseControl::new();

        // press left button
        mc.mouse_action([0x20, 0x00, 0x00], &mut mouse);
        assert_eq!(mouse.button_pressed.left, true);
        assert_eq!(mc.button_pressed.left, true);

        // press again, no release before
        mouse.button_pressed.left = false;
        mc.mouse_action([0x20, 0x00, 0x00], &mut mouse);
        assert_eq!(mouse.button_pressed.left, false);  // unchanged
        assert_eq!(mc.button_pressed.left, true);

        // release left button
        mc.mouse_action([0x20, 0x00, 0x01], &mut mouse);
        assert_eq!(mouse.button_pressed.left, false); 
        assert_eq!(mc.button_pressed.left, false);
        
        // release again, no press before
        mouse.button_pressed.left = true;
        mc.mouse_action([0x20, 0x00, 0x01], &mut mouse);
        assert_eq!(mouse.button_pressed.left, true); // unchanged
        assert_eq!(mc.button_pressed.left, false);
        

        // press middle button
        mc.mouse_action([0x20, 0x00, 0x02], &mut mouse);
        assert_eq!(mouse.button_pressed.middle, true);
        assert_eq!(mc.button_pressed.middle, true);

        // press again, no release before
        mouse.button_pressed.middle = false;
        mc.mouse_action([0x20, 0x00, 0x02], &mut mouse);
        assert_eq!(mouse.button_pressed.middle, false);  // unchanged
        assert_eq!(mc.button_pressed.middle, true);

        // release middle button
        mc.mouse_action([0x20, 0x00, 0x03], &mut mouse);
        assert_eq!(mouse.button_pressed.middle, false); 
        assert_eq!(mc.button_pressed.middle, false);
        
        // release again, no press before
        mouse.button_pressed.middle = true;
        mc.mouse_action([0x20, 0x00, 0x03], &mut mouse);
        assert_eq!(mouse.button_pressed.middle, true); // unchanged
        assert_eq!(mc.button_pressed.middle, false);


        // press right button
        mc.mouse_action([0x20, 0x00, 0x04], &mut mouse);
        assert_eq!(mouse.button_pressed.right, true);
        assert_eq!(mc.button_pressed.right, true);

        // press again, no release before
        mouse.button_pressed.right = false;
        mc.mouse_action([0x20, 0x00, 0x04], &mut mouse);
        assert_eq!(mouse.button_pressed.right, false);  // unchanged
        assert_eq!(mc.button_pressed.right, true);

        // release right button
        mc.mouse_action([0x20, 0x00, 0x05], &mut mouse);
        assert_eq!(mouse.button_pressed.right, false); 
        assert_eq!(mc.button_pressed.right, false);
        
        // release again, no press before
        mouse.button_pressed.right = true;
        mc.mouse_action([0x20, 0x00, 0x05], &mut mouse);
        assert_eq!(mouse.button_pressed.right, true); // unchanged
        assert_eq!(mc.button_pressed.right, false);
    }

}