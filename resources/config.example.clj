;; Program configuration
;; This is an example configuration, the program actually reads this from /resources/config.clj
;; You need to copy this file and setup your own properties here
;;
{ 
  ;; The sftp host to connect to
  :sftp-host "sftp.example.com"

  ;; This program uses public key auth to log into servers, this is your public
  ;; private key-pair
  :private-key "/path/to/private/key"
  :public-key "/path/to/public/key"

  ;; The base path where the connection will cd into, thus making it your
  ;; base-path
  :base-path "./some/dir"
}
