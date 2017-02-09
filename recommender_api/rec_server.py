from flask import Flask, request, current_app, jsonify
from elasticsearch import Elasticsearch

app = Flask(__name__)


def get_es():
    if not hasattr(current_app, 'es'):
        current_app.es = connect_es()
    return current_app.es


def connect_es():
    return Elasticsearch(hosts='localhost:9200', use_ssl=False, verify_cert=False, timeout=600)

def search(id):
    es = get_es()
    param = {"_source_include":"wiki_topics"}
    vector = es.get("query_idx", id, "tv", params=param)["_source"].get('wiki_topic')

    if vector is None:
        vector = [0,0,0,0]

    script_faen = """
    double total = 0;
    def document_vector = doc['wiki_topic'] ?: [0,0,0,0];
    for (int i = 0; i < document_vector.length; ++i)
        {
            total += document_vector[i] * params.w[i];
        }
    return total;
    """

    body = {
        "query": {
            "function_score": {
                "script_score": {
                    "script" : {
                        "inline": script_faen,
                        "lang": "painless",
                        "params": {
                            "w": vector
                        }
                    }
                }
            }
        }
    }

    return es.search("query_idx", "tv", body=body)

@app.route('/recommend', methods=['GET'])
def recommend():
    id = request.args.get("id")
    return jsonify(search(id))



if __name__ == '__main__':
    app.run(debug=True)