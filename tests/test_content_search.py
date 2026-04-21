import urllib.parse

from app.services import content_service


def test_curseforge_query_variants_include_atm_aliases():
    variants = [item.lower() for item in content_service._build_curseforge_query_variants("all the mods 10")]

    assert variants[0] == "all the mods 10"
    assert "atm10" in variants
    assert "allthemods10" in variants


def test_search_curseforge_prefers_relevant_hits_from_later_pages(monkeypatch):
    def fake_request_json(url: str, headers=None):
        parsed = urllib.parse.urlparse(url)
        params = urllib.parse.parse_qs(parsed.query)
        query = params.get("searchFilter", [""])[0]
        index = int(params.get("index", ["0"])[0])

        if query == "all the mods 10" and index == 0:
            return {
                "data": [
                    {
                        "id": 100,
                        "name": "Random Sky Pack",
                        "summary": "generic",
                        "downloadCount": 2_000_000,
                        "slug": "random-sky-pack",
                    }
                ]
            }
        if query == "all the mods 10" and index == 50:
            return {
                "data": [
                    {
                        "id": 101,
                        "name": "All the Mods 10",
                        "summary": "official atm10 pack",
                        "downloadCount": 20_000,
                        "slug": "all-the-mods-10",
                    }
                ]
            }
        if query == "atm10" and index == 0:
            return {
                "data": [
                    {
                        "id": 102,
                        "name": "All the Mods 10 - ATM10",
                        "summary": "atm10",
                        "downloadCount": 100_000,
                        "slug": "atm10",
                    }
                ]
            }
        return {"data": []}

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(content_service, "_curseforge_headers", lambda: {"x-api-key": "test"})

    results = content_service.search_curseforge(
        query="all the mods 10",
        mc_version=None,
        loader=None,
        content_type="modpack",
        release_channel="all",
    )

    assert len(results) >= 2
    assert results[0]["id"] in {101, 102}
    assert any(item["id"] == 101 for item in results)
    assert any(item["id"] == 102 for item in results)
