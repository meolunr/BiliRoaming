#!/usr/bin/env python

import json


def main():
    with open('list.json') as file:
        json_data = json.load(file)
        output_list = parse(json_data)
        with open('output.txt', 'w') as output_file:
            output_file.write(' '.join(output_list))
            print('Ignore decompiled packages has been written to output.txt')


def parse(json_data: dict):
    output_list = list()

    for k, v in json_data.items():
        for i in v:
            if isinstance(i, str):
                output_list.append((k + '.' + i) if k != 'root' else i)
            elif isinstance(i, dict):
                result = map(lambda x: k + '.' + x if k != 'root' else x, parse(i))
                output_list.extend(result)

    return output_list


if __name__ == '__main__':
    main()
