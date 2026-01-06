
use mobile_tilt_mouse::connection_handler;
use mobile_tilt_mouse::Result;

fn main() -> Result<()> {

    connection_handler(false)?;

    Ok(())
}
