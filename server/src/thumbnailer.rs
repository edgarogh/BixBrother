use duct::cmd;
use std::path::{Path, PathBuf};
use std::process::Command;
use tokio::sync::RwLock;

pub struct Thumbnailer {
    path: PathBuf,
    make_thumbnail_lock: RwLock<()>,
}

fn find_subsequence<T>(haystack: &[T], needle: &[T]) -> Option<usize>
where
    for<'a> &'a [T]: PartialEq,
{
    haystack
        .windows(needle.len())
        .position(|window| window == needle)
}

impl Thumbnailer {
    pub fn new() -> Self {
        let path = PathBuf::from("./thumbnails");
        let help_message = Command::new("create-static-map")
            .arg("--help")
            .output()
            .expect("create-static-map not installed")
            .stderr;
        assert!(
            find_subsequence(&help_message, "--attribution".as_bytes()).is_some(),
            "create-static-map doesn't support --attribution"
        );
        cmd!("convert", "--version")
            .read()
            .expect("ImageMagick isn't installed");
        std::fs::create_dir_all(&path).unwrap();
        Self {
            path,
            make_thumbnail_lock: RwLock::new(()),
        }
    }

    pub async fn thumbnail(&self, lat: f32, lon: f32) -> eyre::Result<Vec<u8>> {
        let file_path = self.path.join(format!("{lat},{lon}.png"));
        let lock = self.make_thumbnail_lock.read().await;
        match tokio::fs::read(&file_path).await {
            Ok(bytes) => Ok(bytes),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                drop(lock);
                let lock = self.make_thumbnail_lock.write().await;
                make_thumbnail(lat, lon, &file_path).await?;
                drop(lock);
                Ok(tokio::fs::read(&file_path)
                    .await
                    .expect("should have been created"))
            }
            Err(e) => Err(e.into()),
        }
    }
}

async fn make_thumbnail(lat: f32, lon: f32, save_to: &Path) -> eyre::Result<()> {
    let expr = cmd!(
        "create-static-map",
        "-m",
        format!("{lat},{lon}"),
        "-z",
        "16",
        "--width",
        "256",
        "--height",
        "256",
        "--attribution",
        "",
        "--output",
        "/dev/stdout",
    )
    .pipe(cmd!(
        "convert",
        "png:-",
        "-gravity",
        "center",
        "-crop",
        "128x128+0+0",
        "+repage",
        save_to,
    ));

    tokio::task::spawn_blocking(move || expr.run())
        .await
        .unwrap()?;

    Ok(())
}
