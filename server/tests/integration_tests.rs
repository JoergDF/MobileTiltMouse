use std::{path::PathBuf, time::Duration};
use std::thread;
use std::sync::mpsc;
use enigo::{Button, Direction, Mouse, Axis, Coordinate};
use std::process::Command;

use mobile_tilt_mouse::connection_handler;

// Test command for running the following integration tests (must run sequentially, hence --test-threads=1):
// cargo test --test integration_tests -- --ignored --test-threads=1

const IOS_SIMULATOR_DEVICE: &str = "iPhone 17";
const ANDROID_EMULATOR_DEVICE: &str = "Pixel_9_API_36.0";

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
        connection_handler(&mut mouse, true).unwrap();
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
#[cfg(target_os = "macos")]
// Start iOS simulator and run test case there which clicks buttons. Server should receive the button click commands.
// Test command:
// cargo test --test integration_tests -- test_ios_receive_button_clicks --ignored
fn test_ios_receive_button_clicks() {
    // iOS test part is only run if the following environment variable is set: TEST_RUNNER_Integration_Testing="YES"
    let mut simulator_process = Command::new("xcodebuild")
    .env("TEST_RUNNER_Integration_Testing", "YES")
    .args(["test", "-scheme", "MobileTiltMouse", "-destination", format!("platform=iOS Simulator,name={}", IOS_SIMULATOR_DEVICE).as_str(), "-only-testing", "MobileTiltMouseUITests/IntegrationTests/testSendButtonClicks"])
    .current_dir("../AppiOS")
    .spawn()
    .expect("Failed to execute integration test counterpart in Xcode.");

    receive_button_clicks();

    simulator_process.wait().expect("Failed to wait for simulator process.");
}

#[test]
#[ignore]
// Start Android emulator and run test case there which clicks buttons. Server should receive the button click commands.
// Test command:
// cargo test --test integration_tests -- test_android_receive_button_clicks --ignored
fn test_android_receive_button_clicks() {
    let emulator_path =
    if cfg!(target_os = "macos") {
        // get home directory of user and add remaining emulator path
        let homedir = std::env::home_dir().expect("Failed to get home directory.");
        homedir.join("Library/Android/sdk/emulator/emulator")
    } else if cfg!(target_os = "linux") {
        let homedir = std::env::home_dir().expect("Failed to get home directory.");
        homedir.join("Android/sdk/emulator/emulator")
    } else if cfg!(target_os = "windows") {
        let appdir = std::env::var("LOCALAPPDATA").expect("Failed to get environment variable LOCALAPPDATA.");
        PathBuf::from(appdir).join(r"Android\Sdk\emulator\emulator.exe")
    } else {
        panic!("OS not supported")
    };

    // start the Android emulator
    let mut emulator_process = Command::new(emulator_path)
    .args(["-avd", ANDROID_EMULATOR_DEVICE])
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
