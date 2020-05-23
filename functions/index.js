const functions = require('firebase-functions')
const admin = require('firebase-admin')
admin.initializeApp()

const db = admin.firestore()

async function deviationNotifier(snapshot, context) {
      var workerDoc = await db.collection('rmpWorkers').doc(context.params.workerUid).get()
      var officerDoc = await db.collection('officers').doc(workerDoc.data().officer).get()

      const data = {
            data: {
                  by: workerDoc.data().email,
                  reportTime: String(snapshot.data().reportTime),
                  id: String(workerDoc.createTime.toMillis()).substr(String(workerDoc.createTime).length - 5)
            },
            token: officerDoc.data().token
      }

      console.log(workerDoc.data(), officerDoc.data(), data)

      return admin.messaging().send(data).catch((reason) => {
            console.log(reason)
      })
}

exports.deviationNotifier = functions.firestore
      .document("/rmpWorkers/{workerUid}/trips/{tripDocId}/deviations/{deviationDocId}")
      .onCreate(deviationNotifier)