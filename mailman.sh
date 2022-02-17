baseUrl=""
username=""
password=""
filter='[]'
token="$(jq -n --arg username "$username" --arg password "$password" '{"Username":$username,"Password":$password,"RememberMe":false}' | curl -sSf -i -d @- -H 'Content-Type: application/json' "$baseUrl/api/v1/account/login" | jq -Rr --slurp 'split("\r?\n";"")|map(capture("set-cookie: (?<token>Login.*?);.*"))[0].token')"
filteredMails="$(curl -sSf -H "cookie: $token" "$baseUrl/api/v1/Posteingang/GetByEingangsmedium?eingangsmediumSystemName=email" | jq --argjson filter "$filter" 'map(select(. as $mail|$filter|map(select(length>=3)|. as $e|$mail.Bezeichnung|index($e)!=null)|any))')"
echo "$filteredMails" | jq -c '.[]' | while IFS= read -r mail; do
  echo "Deleting $(echo "$mail" | jq -r '.Bezeichnung')"
  response="$(jq -n -c --argjson mail "$mail" '[$mail|.ObjectUuid]' | curl -sSf -d @- -H 'Content-Type: application/json' -H "cookie: $token" "$baseUrl/api/v1/Posteingang/Delete")"
  echo "$response"
done
