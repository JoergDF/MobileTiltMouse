
use enigo::{Enigo, Settings};
use mobile_tilt_mouse::connection_handler;
use mobile_tilt_mouse::Result;

fn main() -> Result<()> {

    let mut mouse = Enigo::new(&Settings::default())?;

    connection_handler(&mut mouse, false)?;

    Ok(())
}
