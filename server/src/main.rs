
use std::error::Error;
use enigo::{Enigo, Settings};


use mobile_tilt_mouse::connection_handler;


fn main() -> Result<(), Box<dyn Error + Send + Sync + 'static>> {
    
    let mut mouse = Enigo::new(&Settings::default())?;

    connection_handler(&mut mouse)?;

    Ok(())
}






