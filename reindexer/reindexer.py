# coding=utf-8

import argparse
import json
import sys

from elasticsearch import Elasticsearch


def check_response(response, msg):
    if not response["acknowledged"]:
        print(msg)

def get_index_settings(settings, mappings, types, synonyms = None, stopwords = None):
    with open(settings) as settings_file:
        settings_json = json.load(settings_file)
    with open(mappings) as mappings_file:
        mappings_json = json.load(mappings_file)

    if synonyms is not None:
        with open(synonyms) as synonym_file:
            synonym_list = [line.rstrip() for line in synonym_file]
    else:
        synonym_list = ["i => i"]

    settings_json["index"]["analysis"]["filter"]["no_synonym_filter"]["synonyms"] = synonym_list
    del settings_json["index"]["analysis"]["filter"]["no_synonym_filter"]["synonyms_path"]

    if stopwords is not None:
        with open(stopwords) as stopwords_file:
            stopwords_list = [line.rstrip() for line in stopwords_file]
    else:
        stopwords_list = ["i"]

    settings_json["index"]["analysis"]["filter"]["no_stop"]["stopwords"] = stopwords_list
    del settings_json["index"]["analysis"]["filter"]["no_stop"]["stopwords_path"]

    mappings_dict = {}
    for type in types:
        mappings_dict[type] = mappings_json

    config = {}
    config["settings"] = settings_json
    config["mappings"] = mappings_dict
    return config


def get_reindex_request(source_idx, dest_idx):
    request = {}
    source = {}
    dest = {}
    source["index"] = source_idx
    dest["index"] = dest_idx
    request["source"] = source
    request["dest"] = dest
    return request


def main():
    parser = argparse.ArgumentParser(description='es config')
    parser.add_argument('--authhostandport', default="localhost:9200", type=str, dest='authhostandport', help="The full host/port connection, including auth, if applicable. Ex: https://user:password@elasticsearch.anbefaling.nrk.no:443")
    parser.add_argument('--source_idx', default="query_idx", type=str, dest='source_idx', help="The source index where the original data is stored.")
    parser.add_argument('--dest_idx', default="dest_idx", type=str, dest='dest_idx', help="The new index which will be crated")
    parser.add_argument('--stopwordfile', type=str, dest='stopwordfile', help="The stopwords to be used, one stopword per line, see: https://www.elastic.co/guide/en/elasticsearch/guide/current/using-stopwords.html")
    parser.add_argument('--synonymfile', type=str, dest='synonymfile', help="The synonyms to be used, one synonym per line, see: https://www.elastic.co/guide/en/elasticsearch/guide/master/synonyms-expand-or-contract.html")
    parser.add_argument('--settings', type=str, dest='settingsfile', help="The elastic settings")
    parser.add_argument('--mappings', type=str, dest='mappingsfile', help="The elastic mappings")

    args = parser.parse_args()
    es = Elasticsearch(hosts=args.authhostandport, use_ssl="local" not in args.authhostandport, verify_cert="local" not in args.authhostandport, timeout=600)

    if args.settingsfile == None or args.mappingsfile == None:
        raise ValueError("Settings/mappings files are not provided")

    if args.dest_idx == 'query_idx':
        raise ValueError("query_idx cannot be the destination index")

    if not es.indices.exists(index=args.source_idx):
        raise ValueError("Source idx does not exist.")

    if args.source_idx == args.dest_idx:
        raise ValueError("Source idx and dest idx cannot be identical.")

    if es.indices.exists(index=args.dest_idx):
        print("Deleting existing dest idx '%s'" % args.dest_idx)
        check_response(es.indices.delete(index=args.dest_idx), "Unable to delete idx '%s'" % args.dest_idx)

    config = get_index_settings(args.settingsfile, args.mappingsfile, ['tv', 'super'], synonyms=args.synonymfile, stopwords=args.stopwordfile)

    print("Creating dest idx '%s'" % args.dest_idx)
    check_response(es.indices.create(index=args.dest_idx, body=config, request_timeout=1000), "Unable to create idx '%s'" % args.dest_idx)

    reindex_request = get_reindex_request(args.source_idx, args.dest_idx)
    print("Starting re-indexing from '%s' to '%s'" % (args.source_idx, args.dest_idx))
    res = es.reindex(body=reindex_request, request_timeout=1000)
    print(res)


if __name__ == '__main__':
    main()