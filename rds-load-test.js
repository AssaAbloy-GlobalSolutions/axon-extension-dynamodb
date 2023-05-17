import http from 'k6/http';
import {check, fail, sleep} from 'k6';

// export const options = {
//     executor: 'ramping-vus',
//     startVUs: 0,
//     stages: [
//         { duration: '1m', target: 10 },
//         { duration: '20s', target: 25 },
//         // { duration: '5m', target: 25 },
//         // { duration: '1m', target: 50 },
//         // { duration: '5m', target: 50 },
//         // { duration: '1m', target: 75 },
//         // { duration: '10m', target: 75 },
//         // { duration: '2m', target: 100 },
//         // { duration: '10m', target: 100 },
//     ],
// };

const baseUrl = "http://AxonR-axonr-P6W0GP0H4W5A-1384001342.eu-west-1.elb.amazonaws.com/bank"

export default function () {
    let accountId = createAccount()
    sleep(.25)
    for (let i = 0; i < 10; i++) {
        makeDeposit(accountId)
        sleep(.25)
    }
    sleep(.75)
    getAccountDetails(accountId)
}

function createAccount() {
    const accountUrl = `${baseUrl}/account`
    const response = http.post(accountUrl)
    if (!check(response, {
        'createAccount status 200': (r) => r.status === 200,
    })) {
        throw `Failed response for createAccount response.status`
    }
    return response.json().accountId
}

function makeDeposit(accountId) {

    let depositRequest = JSON.stringify({
        accountId: accountId,
        amount: 1
    })
    const accountDepositUrl = `${baseUrl}/account/deposit`
    const response = http.post(accountDepositUrl, depositRequest, {
        headers: {
            'Content-Type': 'application/json',
        }
    })
    if (!check(response, {
        'deposit status 200': (r) => r.status === 200,
    })) {
        throw `Failed deposit response with: ${response.status}`
    }
}

function getAccountDetails(accountId) {
    let getAccountRequest = JSON.stringify({
        accountId: accountId,
    })
    const getAccountURl = `${baseUrl}/account`
    let response = ""
    for (let i = 0; i < 4; i++) {
        if (i > 0) {
            console.log('attempt:' + i)
        }
        try {
            response = http.request('GET', getAccountURl, getAccountRequest, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                }
            });
            if (!check(response, {
                'get Account request status 200': (r) => r.status === 200,
                'check amount:': (r) => r.json().balance === 10
            })) {
                // console.log(`Failed eventNotification response with requestId: ${requestId} - response.status: ${JSON.stringify(response.status)} response.subStatusCode: ${JSON.stringify(response.json().responseHeader.statusCode)}`)

                throw 'failed account balance check'
            }
            break
        } catch (e) {
            sleep(.25)
            if (i === 3) {
                console.log(`Failed account balance check`)
                fail(`Failed account balance check for ${accountId} and balance: ${response.json().balance}`)
            }
        }
    }
}