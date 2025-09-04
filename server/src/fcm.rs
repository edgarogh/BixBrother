use crate::StationStatus;
use eyre::Context;
use google_fcm1::api::{AndroidConfig, Message, SendMessageRequest};
use google_fcm1::yup_oauth2::ServiceAccountAuthenticator;
use google_fcm1::{FirebaseCloudMessaging, hyper_rustls, yup_oauth2};
use hyper_rustls::HttpsConnector;
use hyper_util::client::legacy::connect::HttpConnector;
use std::collections::HashMap;
use uuid::Uuid;

pub struct FcmService {
    client: FirebaseCloudMessaging<HttpsConnector<HttpConnector>>,
}

impl FcmService {
    pub async fn from_service_file() -> eyre::Result<FcmService> {
        let google_credentials = std::env::var("GOOGLE_APPLICATION_CREDENTIALS")
            .wrap_err("env var GOOGLE_APPLICATION_CREDENTIALS is required")?;

        let key = yup_oauth2::read_service_account_key(google_credentials)
            .await
            .wrap_err("$GOOGLE_APPLICATION_CREDENTIALS is not a valid service account key")?;

        let auth = ServiceAccountAuthenticator::builder(key)
            .build()
            .await
            .wrap_err("failed to create service account")?;

        rustls::crypto::ring::default_provider()
            .install_default()
            .unwrap();

        let client =
            hyper_util::client::legacy::Client::builder(hyper_util::rt::TokioExecutor::new())
                .build(
                    hyper_rustls::HttpsConnectorBuilder::new()
                        .with_native_roots()
                        .unwrap()
                        .https_or_http()
                        .enable_http1()
                        .enable_http2()
                        .build(),
                );

        Ok(Self {
            client: FirebaseCloudMessaging::new(client, auth),
        })
    }

    async fn send_once(
        &mut self,
        external_id: Uuid,
        status: StationStatus,
        now: i64,
    ) -> google_fcm1::Result<()> {
        let mut data = HashMap::new();
        data.insert("i".into(), external_id.to_string());
        data.insert("b".into(), status.num_bikes_available.to_string());
        data.insert("e".into(), status.num_ebikes_available.to_string());
        data.insert("a".into(), status.num_docks_available.to_string());
        data.insert("u".into(), now.to_string());

        let external_id_str = external_id.to_string();
        let message = SendMessageRequest {
            message: Some(Message {
                topic: Some(format!("v1status_{external_id_str}")),
                data: Some(data),
                android: Some(AndroidConfig {
                    collapse_key: Some(external_id_str),
                    ttl: Some(chrono::Duration::hours(1)),
                    ..Default::default()
                }),
                ..Default::default()
            }),
            validate_only: None,
        };

        self.client
            .projects()
            .messages_send(message, "projects/bix-brother")
            .doit()
            .await
            .map(|_| ())
    }

    pub async fn send(
        &mut self,
        external_id: Uuid,
        status: StationStatus,
        now: i64,
    ) -> google_fcm1::Result<()> {
        let mut exponential_backoff = 1usize;
        loop {
            match self.send_once(external_id, status, now).await {
                Ok(()) => break Ok(()),
                Err(google_fcm1::Error::BadRequest(value)) if value["error"]["code"] == 500 => {
                    if exponential_backoff > 300 {
                        break Err(google_fcm1::Error::BadRequest(value));
                    }
                }
                Err(err) => break Err(err),
            }

            tokio::time::sleep(std::time::Duration::from_secs(exponential_backoff as u64)).await;
            exponential_backoff *= 2;
        }
    }
}
