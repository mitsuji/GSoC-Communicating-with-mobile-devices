{-# LANGUAGE OverloadedStrings, TypeFamilies, TemplateHaskell,
               QuasiQuotes, MultiParamTypeClasses, GeneralizedNewtypeDeriving, FlexibleContexts, GADTs #-}
               
module Network.PushNotify.General ( 
      Device(..)
    , PushNotification(..)
    , PushAppConfig(..)
    , PushResult(..)
    , send
    ) where

import Network.PushNotify.Gcm.Types
import Network.PushNotify.Apns.Types
import Network.PushNotify.Mpns.Types
import Network.PushNotify.Gcm.Send
import Network.PushNotify.Apns.Send
import Network.PushNotify.Mpns.Send
import Network.HTTP.Conduit
import Network.HTTP.Types.Status
import Control.Exception

import Data.Text    (Text,pack)
import Data.Default

data Device =   GCM  RegId
            |   APNS DeviceToken
            |   MPNS DeviceURI
            deriving (Show, Read, Eq)

data PushNotification = PushNotification {
        apnsNotif :: Maybe APNSmessage
    ,   gcmNotif  :: Maybe GCMmessage
    ,   mpnsNotif :: Maybe MPNSmessage    
    } deriving Show

instance Default PushNotification where
    def = PushNotification Nothing Nothing Nothing


data PushAppConfig = PushAppConfig{
        gcmAppConfig  :: Maybe GCMAppConfig
    ,   apnsAppConfig :: Maybe APNSAppConfig
    ,   mpnsAppConfig :: Maybe MPNSAppConfig
    }

instance Default PushAppConfig where
    def = PushAppConfig Nothing Nothing Nothing


data PushResult = PushResult {
        successful   :: [Device]
    ,   failed       :: [(Device,Either Text SomeException)]
    ,   toResend     :: [Device]
    ,   unRegistered :: [Device]
    ,   newIds       :: [(Device,Device)]
    } deriving Show

instance Default PushResult where
    def = PushResult [] [] [] [] []

class IsPushResult a where
    toPushResult :: a -> PushResult

instance IsPushResult GCMresult where
    toPushResult r = def {
        successful   = map (GCM . fst)               $ messagesIds r
    ,   failed       = map (\(x,y) -> (GCM x, y))    $ map (\(x,y) -> (x,Left y))                   (errorRest r) 
                                                    ++ map (\x     -> (x,Left "UnregisteredError")) (errorUnRegistered r)
                                                    ++ map (\x     -> (x,Left "InternalError"))     (errorToReSend     r)        
    ,   toResend     = map GCM                       $ errorToReSend r
    ,   unRegistered = map GCM                       $ errorUnRegistered r ++ (map fst $ errorRest r) 
    -- I decide to unregister all regId with error different to Unregistered or Unavailable. (errorRest)
    -- Because these are non-recoverable error.
    ,   newIds       = map (\(x,y) -> (GCM x,GCM y)) $ newRegids r
    }

instance IsPushResult APNSresult where
    toPushResult r = def {
        successful   = map APNS $ successfulTokens r
    ,   failed       = map (\x -> (APNS x , Left "ComunicatingError")) $ toReSendTokens   r
    ,   toResend     = map (APNS) $ toReSendTokens   r
    }

instance IsPushResult APNSFeedBackresult where
    toPushResult r = def {
        unRegistered = map (APNS . fst) $ unRegisteredTokens r
    }


instance IsPushResult MPNSresult where
    toPushResult = \r -> def {
        successful   = map (MPNS . fst) . filter ((/=) (Just "Dropped") . notificationStatus . snd ) $ sucessfullResults r
    ,   failed       = map (\(x,y) -> (MPNS x , Right y)) $ errorException r
    ,   toResend     = map (MPNS . fst) . filter (error500 . snd) $ errorException r
    ,   unRegistered = map (MPNS . fst) . filter ((==) (Just "Expired") . subscriptionStatus . snd ) $ sucessfullResults r
    } where
        error500 :: SomeException -> Bool
        error500 e = case (fromException e) :: Maybe HttpException of
                         Just (StatusCodeException status _ _) -> (statusCode status) >= 500
                         _                                     -> False

forgetConst :: Device -> Text
forgetConst (GCM  x) = x
forgetConst (APNS x) = x
forgetConst (MPNS x) = x

isGCM  (GCM  _) = True
isGCM  _        = False

isAPNS (APNS _) = True
isAPNS _        = False

isMPNS (MPNS _) = True
isMPNS _        = False

send :: (Maybe Manager) -> (Maybe APNSManager) -> PushAppConfig -> PushNotification -> [Device] -> IO PushResult
send man apnsMan config notif devices = do
                let
                    gcmDevices  = map forgetConst $ filter isGCM  devices
                    apnsDevices = map forgetConst $ filter isAPNS devices 
                    mpnsDevices = map forgetConst $ filter isMPNS devices

                r1 <- case (gcmDevices , gcmAppConfig config , gcmNotif  notif , man) of
                          (_:_,Just cnf,Just msg,Just m) -> do
                                                               res <- sendGCM m cnf msg{registration_ids = gcmDevices}
                                                               return $ toPushResult res
                          _                              -> return def

                r2 <- case (apnsDevices , apnsNotif notif , apnsMan) of
                          (_:_,Just msg,Just m) -> do
                                                       res <- sendAPNS m msg{deviceTokens = apnsDevices}
                                                       return $ toPushResult res
                          _                     -> return def

                r3 <- case (mpnsDevices , mpnsAppConfig config , mpnsNotif notif , man) of
                          (_:_,Just cnf,Just msg,Just m) -> do
                                                               res <- sendMPNS m cnf msg{deviceURIs = mpnsDevices}
                                                               return $ toPushResult res
                          _                              -> return def

                return $ combine (combine r1 r2) r3
                where
                    combine (PushResult a1 b1 c1 d1 e1) (PushResult a2 b2 c2 d2 e2) = PushResult (a1 ++ a2) (b1 ++ b2) (c1 ++ c2) (d1 ++ d2) (e1 ++ e2)