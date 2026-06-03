#!/usr/bin/env bash
# source scripts/api.sh
# Usage examples at the bottom of this file.

BASE="http://localhost:8080/api"
H='Content-Type: application/json'

# ── Auth ──────────────────────────────────────────────────────────────────────
login()       { curl -s -X POST "$BASE/auth/login"    -H "$H" -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -m json.tool; }
register()    { curl -s -X POST "$BASE/auth/register" -H "$H" -d "{\"username\":\"$1\",\"password\":\"$2\",\"email\":\"$3\",\"fullName\":\"$4\",\"role\":\"$5\",\"city\":\"$6\",\"province\":\"$7\"}" | python3 -m json.tool; }

# ── Restaurants ───────────────────────────────────────────────────────────────
restaurants()        { curl -s "$BASE/restaurants?city=${1:-Istanbul}"               | python3 -m json.tool; }
searchrestaurants()  { curl -s "$BASE/restaurants?city=${2:-Istanbul}&keyword=$1" | python3 -m json.tool; }
restaurant()         { curl -s "$BASE/restaurants/$1"                                | python3 -m json.tool; }
myrestaurants()      { curl -s "$BASE/restaurants/manager/$1"                        | python3 -m json.tool; }
menu()               { curl -s "$BASE/restaurants/$1/menu"                           | python3 -m json.tool; }
categories()         { curl -s "$BASE/restaurants/$1/categories"                     | python3 -m json.tool; }
ratings()            { curl -s "$BASE/restaurants/$1/ratings"                        | python3 -m json.tool; }

# ── Orders ────────────────────────────────────────────────────────────────────
# placeorder <customerId> <restaurantId> [couponCode] <itemId:qty> [itemId:qty ...]
# e.g.  placeorder 9 4 "" 12:1 13:1
#        placeorder 9 4 BURGER5 12:1 13:1
placeorder() {
  local cid=$1 rid=$2 coupon=$3; shift 3
  local items="["
  for pair in "$@"; do
    iid=${pair%%:*}; qty=${pair##*:}
    items+="{\"itemId\":$iid,\"quantity\":$qty},"
  done
  items="${items%,}]"
  local body="{\"customerId\":$cid,\"restaurantId\":$rid,\"items\":$items"
  [[ -n "$coupon" ]] && body+=",\"couponCode\":\"$coupon\""
  body+="}"
  curl -s -X POST "$BASE/orders" -H "$H" -d "$body" | python3 -m json.tool
}

order()           { curl -s "$BASE/orders/$1"                             | python3 -m json.tool; }
orderitems()      { curl -s "$BASE/orders/$1/items"                       | python3 -m json.tool; }
myorders()        { curl -s "$BASE/orders/customer/$1"                    | python3 -m json.tool; }
restaurantorders(){ curl -s "$BASE/orders/restaurant/$1"                  | python3 -m json.tool; }
pendingorders()   { curl -s "$BASE/orders/restaurant/$1/pending"          | python3 -m json.tool; }
acceptorder()     { curl -s -X PUT "$BASE/orders/$1/accept" -H "$H" -d "{\"managerId\":$2}" | python3 -m json.tool; }
arriveorder()     { curl -s -X PUT "$BASE/orders/$1/arrive"  -H "$H" -d "{\"managerId\":$2}" | python3 -m json.tool; }
rateorder()       { curl -s -X POST "$BASE/orders/$1/rate" -H "$H" -d "{\"customerId\":$2,\"score\":$3,\"comment\":\"$4\"}" | python3 -m json.tool; }

# ── Coupons ───────────────────────────────────────────────────────────────────
coupons()         { curl -s "$BASE/restaurants/$1/coupons"                | python3 -m json.tool; }

# ── Addresses ─────────────────────────────────────────────────────────────────
addresses()       { curl -s "$BASE/users/$1/addresses"                    | python3 -m json.tool; }
addaddress()      { curl -s -X POST "$BASE/users/$1/addresses" -H "$H" -d "{\"city\":\"$2\",\"province\":\"$3\"}" | python3 -m json.tool; }
deleteaddress()   { curl -s -X DELETE "$BASE/users/addresses/$2?userId=$1"; echo; }
selectaddress()   { curl -s -X PUT "$BASE/users/$1/selected-address" -H "$H" -d "{\"addressId\":$2}" | python3 -m json.tool; }

# ── Phones ────────────────────────────────────────────────────────────────────
phones()          { curl -s "$BASE/users/$1/phones"                       | python3 -m json.tool; }
addphone()        { curl -s -X POST "$BASE/users/$1/phones" -H "$H" -d "{\"phone\":\"$2\"}" | python3 -m json.tool; }
deletephone()     { curl -s -X DELETE "$BASE/users/phones/$2?userId=$1";  echo; }

# ── Statistics ────────────────────────────────────────────────────────────────
stats()           { curl -s "$BASE/statistics/restaurant/$1/monthly"      | python3 -m json.tool; }

# ── Quick pipeline ────────────────────────────────────────────────────────────
# fullpipeline <customerId> <managerId> <restaurantId> <itemId:qty> [coupon]
# e.g.  fullpipeline 9 1 1 3:1
fullpipeline() {
  local cid=$1 mid=$2 rid=$3 item=$4 coupon=${5:-""}
  echo "=== placing order (status: SENT) ===" && placeorder $cid $rid "$coupon" $item
  echo -n "order id? " && read oid
  echo "=== accepting (SENT → PREPARING) ===" && acceptorder $oid $mid
  echo "=== arriving  (PREPARING → ARRIVED) ===" && arriveorder $oid $mid
  echo "=== done — rate with: rateorder $oid $cid <1-5> 'comment' ==="
}

echo "api.sh loaded — BASE=$BASE"
echo "functions: login register restaurants searchrestaurants restaurant myrestaurants"
echo "           menu categories ratings placeorder order orderitems myorders"
echo "           restaurantorders pendingorders acceptorder arriveorder rateorder"
echo "           coupons addresses addaddress deleteaddress selectaddress"
echo "           phones addphone deletephone stats fullpipeline"
