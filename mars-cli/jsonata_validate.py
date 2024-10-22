import jsonata
import requests
import csv
import json
import argparse

def main(filepath='../test-data/biosamples-original-isa.json', 
         table_url='https://docs.google.com/spreadsheets/d/e/2PACX-1vQvgQoUByiJgGcJ4jtD8bG9AyQrh4TYVQE8aq7AqJRxLdfyLFATKspu_vkyqbVsTyEnNIBHqWtpgV6X/pub?gid=0&single=true&output=csv'): 
    """
    Main function to validate JSON data using a JSONata expression extracted from a CSV file.
    
    Parameters:
    filepath (str): The path to the JSON file to be validated.
    table_url (str): The URL of the CSV file hosted online that contains JSONata expressions.
    
    Returns:
    None: This function prints the index and value of invalid JSONata expressions.
    """

    try:
        # Fetch CSV data from the provided URL
        res = requests.get(table_url)
        res.raise_for_status()  # Check for HTTP request errors
    except requests.exceptions.RequestException as e:
        print(f"Error fetching the table from the URL: {e}")
        return

    try:
        # Parse the fetched CSV data into a list of rows
        table = csv.reader(res.text.split('\n'))
        jsonata_list = list(table)
    except Exception as e:
        print(f"Error reading CSV data: {e}")
        return

    try:
        # Open and load the JSON file
        with open(filepath, 'r') as file:
            data = json.load(file)
    except FileNotFoundError:
        print(f"Error: The file '{filepath}' was not found.")
        return
    except json.JSONDecodeError as e:
        print(f"Error parsing JSON data: {e}")
        return

    # Loop through each row in the JSONata list starting from index 3
    for index, ele in enumerate(jsonata_list): 
        if len(ele) > 6 and ele[6] != "" and index > 2:  # Check if there is a valid JSONata expression
            try:
                # Evaluate the JSONata expression
                expr = jsonata.Jsonata("'"+ele[6])  # Correcting the JSONata expression input
                result = expr.evaluate(data)

                if result == False:  # If the evaluation fails, print details
                    print(f"Validation failed at row {index}:")
                    print(f"Sample ID: {ele[0]}")
                    print(f"Expression: {ele[6]}")
                    print(f"Result: {result}")
            except Exception as e:
                print(f"Error evaluating JSONata at row {index}: {e}")
                continue

if __name__ == '__main__':
    # help(main)
    # Argument parser for command line inputs
    parser = argparse.ArgumentParser(description='Validate JSON data using JSONata expressions from a CSV file.')
    
    # Define command-line arguments
    parser.add_argument('--filepath', type=str, required=True, help='Path to the JSON file to be validated.')
    parser.add_argument('--table_url', type=str, required=False, help='URL of the CSV file containing JSONata expressions.', default='https://docs.google.com/spreadsheets/d/e/2PACX-1vQvgQoUByiJgGcJ4jtD8bG9AyQrh4TYVQE8aq7AqJRxLdfyLFATKspu_vkyqbVsTyEnNIBHqWtpgV6X/pub?gid=0&single=true&output=csv')
    
    # Parse the arguments
    args = parser.parse_args()

    # Call the main function with arguments from the command line
    main(filepath=args.filepath, table_url=args.table_url)