use std::time::Duration;
use std::thread;
use std::sync::mpsc;
use enigo::{Button, Direction, Mouse, Axis, Coordinate};
use std::process::Command;
use dirs;

use mobile_tilt_mouse::connection_handler;

// Test command for running the following integration tests (must run sequentially, hence --test-threads=1):
// cargo test --test integration_tests -- --ignored --test-threads=1


struct MockMouse {
    location: (i32, i32),
    display_size: (i32, i32),
    tx: mpsc::Sender<i32>,
}

impl Mouse for MockMouse {
    fn move_mouse(&mut self, _x: i32, _y: i32, _: Coordinate) -> enigo::InputResult<()> {
        Ok(())
    }

    fn scroll(&mut self, _length: i32, _axis: Axis) -> enigo::InputResult<()> {
        Ok(())
    }

    fn button(&mut self, button: Button, direction: Direction) -> enigo::InputResult<()> {
        if button == Button::Left {
            if direction == Direction::Press {
                self.tx.send(0).unwrap();
            } else if direction == Direction::Release {
                self.tx.send(1).unwrap();                
            }
        } else if button == Button::Middle {
            if direction == Direction::Press {
                self.tx.send(2).unwrap();
            } else if direction == Direction::Release {
                self.tx.send(3).unwrap();                
            }
        } else if button == Button::Right {
            if direction == Direction::Press {
                self.tx.send(4).unwrap();
            } else if direction == Direction::Release {
                self.tx.send(5).unwrap();                
            }
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


fn receive_button_clicks() {
    let (tx, rx) = mpsc::channel();
    
    let mut mouse = MockMouse {
        location: (0, 0),
        display_size: (100, 100),
        tx,
    };

    thread::spawn(move || {
        connection_handler(&mut mouse).unwrap();
    });

    // press/release left/middle/right button
    assert_eq!(rx.recv_timeout(Duration::from_millis(180000)), Ok(0));
    assert_eq!(rx.recv_timeout(Duration::from_millis(1000)), Ok(1));
    assert_eq!(rx.recv_timeout(Duration::from_millis(1000)), Ok(2));
    assert_eq!(rx.recv_timeout(Duration::from_millis(1000)), Ok(3));
    assert_eq!(rx.recv_timeout(Duration::from_millis(1000)), Ok(4));
    assert_eq!(rx.recv_timeout(Duration::from_millis(1000)), Ok(5));
}

#[test]
#[ignore]
// Adapt paths to your Xcode project
// Test command:
// cargo test --test integration_tests -- test_ios_receive_button_clicks --ignored
fn test_ios_receive_button_clicks() {
    let mut simulator_process = Command::new("xcodebuild")
    .args(["test", "-scheme", "MobileTiltMouse", "-destination", "platform=iOS Simulator,name=iPhone 16", "-only-testing", "MobileTiltMouseUITests/IntegrationTests/testSendButtonClicks"])
    .current_dir("../AppiOS")
    .spawn()
    .expect("Failed to execute integration test counterpart in Xcode.");

    receive_button_clicks();

    simulator_process.wait().expect("Failed to wait for simulator process.");
}

#[test]
#[ignore]
// Adapt path to your Android emulator
// Test command:
// cargo test --test integration_tests -- test_android_receive_button_clicks --ignored

fn test_android_receive_button_clicks() {
    // get home directory of user
    let homedir = dirs::home_dir().expect("Failed to get home directory.");
    
    // start the Android emulator
    let mut emulator_process = Command::new(homedir.join("Library/Android/sdk/emulator/emulator"))
    .args(["-avd", "Medium_Phone_API_35"])
    .spawn()
    .expect("Failed to execute Android emulator.");

    // wait for the emulator to start
    thread::sleep(Duration::from_secs(10));

    // run the Android counterpart of the integration test
    let mut gradlew_process = Command::new("../AppAndroid/gradlew")
    .args([
        "connectedAndroidTest", 
        "-Pandroid.testInstrumentationRunnerArguments.class=com.example.mobiletiltmouse.IntegrationTest", 
        "-Pandroid.testInstrumentationRunnerArguments.remoteArg=integrationTest"
    ])
    .current_dir("../AppAndroid")
    .spawn()
    .expect("Failed to execute integration test counterpart in Android.");

    // run test
    receive_button_clicks();

    // kill emulator after gradlew process has finished
    gradlew_process.wait().expect("Failed to wait for gradlew process.");
    emulator_process.kill().expect("Failed to kill Android emulator.");
}
