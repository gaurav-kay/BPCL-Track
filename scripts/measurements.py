import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import csv
import datetime

print("Starting...")
cred = credentials.Certificate("./credentials.json")
firebase_admin.initialize_app(cred)

db = firestore.client()

documents = []

print("Retrieving data from database...")
for document in db.collection('measurements').stream():
    documents.append(document.to_dict())
print("Retrieved data...")

fieldnames = [
    'time',
    'by',
    'tlpNumber',
    'tlpType',
    'chainage',
    'latitude',
    'longitude',
    'pspValue',
    'acValue',
    'mgznAnodeValue',
    'maintenanceRequired',
    'remarks',
    'imageUrl'
]

print("Writing to CSV...")
with open('./measurements.csv', 'w') as f:
    writer = csv.DictWriter(f, fieldnames)

    writer.writeheader()
    for document in documents:
        row_dict = {key: document.get(key, None) for key in fieldnames}
        timestamp = datetime.datetime.fromtimestamp(float(document['measurementTime']) / 1000)
        row_dict['time'] = timestamp.strftime('%Y-%m-%d %H:%M:%S')
        row_dict['latitude'] = document['location']['latitude']
        row_dict['longitude'] = document['location']['longitude']

        writer.writerow(row_dict)

print("Finished!")
